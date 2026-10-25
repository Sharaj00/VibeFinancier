package app.vibefinancier;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoreCacheTest {
    @Test public void legacyDatabaseBuildsDerivedPickerIndexWithoutChangingEvents()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-cache-legacy.db";context.deleteDatabase(db);
        try(Store old=new Store(context,db)){old.edit(Ledger.obj("id","cash","entity","account","name","Cash","currency","RUB"));old.getWritableDatabase().execSQL("DROP TABLE picker_entities");old.set("picker_index_ready","");}
        try(Store upgraded=new Store(context,db);Store writer=new Store(context,db)){assertEquals(1,upgraded.metadata().size());assertEquals(1,upgraded.events().size());writer.edit(Ledger.obj("id","food","entity","category","name","Food","direction","expense"));assertEquals(2,upgraded.metadata().size());assertEquals(2,upgraded.events().size());}finally{context.deleteDatabase(db);}
    }
    @Test public void cachedSnapshotsAreCopiesAndObserveAnotherConnection()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-cache-invalidation.db";context.deleteDatabase(db);
        try(Store a=new Store(context,db);Store b=new Store(context,db)){
            a.edit(Ledger.obj("id","cash","entity","account","name","Cash","currency","RUB"));a.entities(false);long token=a.resolvedToken();assertTrue(token>=0);a.entities(false).get(0).put("name","Unsaved");assertEquals("Cash",a.entities(false).get(0).getString("name"));assertEquals(token,a.resolvedToken());
            JSONObject edit=b.type("account",true).get(0);edit.put("name","Bank");b.edit(edit);assertEquals(-1,a.resolvedToken());assertEquals("Bank",a.metadata().get(0).getString("name"));assertEquals("Bank",a.entities(false).get(0).getString("name"));
            JSONObject latest=b.events().get(1);latest.put("checksum","broken");b.getWritableDatabase().execSQL("UPDATE events SET payload=? WHERE id=?",new Object[]{latest.toString(),latest.getString("event_id")});assertThrows(Exception.class,a::metadata);assertThrows(Exception.class,()->a.entities(false));
        }finally{context.deleteDatabase(db);}
    }
    @Test public void metadataAndWarmSnapshotAvoidReplayingThreeThousandTransactions()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();String db="isolated-cache-benchmark.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            List<JSONObject> events=new ArrayList<>();events.add(Ledger.event(Ledger.obj("id","cash","entity","account","name","Cash","currency","RUB"),"","account"));events.add(Ledger.event(Ledger.obj("id","food","entity","category","name","Food","direction","expense","parent",""),"","category"));
            for(int i=0;i<3000;i++)events.add(Ledger.event(Ledger.transaction("expense","cash","1","RUB","food","Synthetic "+"x".repeat(120),"2026-08-01T12:00:00","RUB","1"),"","transaction."+i));store.merge(events,false);
            long start=android.os.SystemClock.elapsedRealtime();assertEquals(2,store.metadata().size());long metadata=android.os.SystemClock.elapsedRealtime()-start;
            start=android.os.SystemClock.elapsedRealtime();assertEquals(3002,store.entities(false).size());long cold=android.os.SystemClock.elapsedRealtime()-start;long token=store.resolvedToken();
            start=android.os.SystemClock.elapsedRealtime();for(int i=0;i<20;i++)assertEquals(token,store.resolvedToken());long warm=android.os.SystemClock.elapsedRealtime()-start;
            android.os.Bundle result=new android.os.Bundle();result.putString("stream","\nCACHE BENCHMARK: 3000 operations; metadata="+metadata+"ms, cold snapshot="+cold+"ms, 20 warm navigation checks="+warm+"ms\n");instrument.sendStatus(0,result);
        }finally{context.deleteDatabase(db);}
    }
    @Test public void softDeleteAndUndoRestoreBalanceWithoutDestroyingHistory()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-swipe-undo.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            store.edit(Ledger.obj("id","cash","entity","account","currency","RUB"));store.edit(Ledger.transaction("income","cash","100","RUB","","","2026-08-01T10:00:00","RUB","1"));store.edit(Ledger.transaction("expense","cash","20","RUB","","","2026-08-01T11:00:00","RUB","1"));assertEquals("80",Money.plain(Ledger.balances(store.entities(false)).get("cash")));
            JSONObject expense=store.type("transaction",true).get(1);expense.put("deleted",true);JSONObject tombstone=store.edit(expense);assertEquals("100",Money.plain(Ledger.balances(store.entities(false)).get("cash")));assertEquals(1,store.entities(true).size());
            expense.put("_head",tombstone.getString("event_id"));expense.put("deleted",false);store.edit(expense);assertEquals("80",Money.plain(Ledger.balances(store.entities(false)).get("cash")));assertEquals(5,store.events().size());
        }finally{context.deleteDatabase(db);}
    }
}
