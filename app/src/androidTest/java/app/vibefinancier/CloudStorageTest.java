package app.vibefinancier;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CloudStorageTest {
    private static class FakeCloud extends Cloud {
        final List<JSONObject> remote=new ArrayList<>();final String tab;int batches=0,creations=0;JSONArray summary;boolean failInitialize=false;
        FakeCloud(String device){super("not-a-token");tab="J_"+device;}
        @Override public void verifySchema(String id)throws Exception{if(id.equals("gone"))throw new Cloud.HttpFailure("Google Sheets",404,"table cells");}
        @Override public List<String> journals(String id){return List.of(tab);}
        @Override public List<JSONObject> readEvents(String id,List<String> tabs){return new ArrayList<>(remote);}
        @Override public String create(String title,java.util.function.Consumer<String> created)throws Exception{creations++;created.accept("candidate");initialize("candidate");return "candidate";}
        @Override public void initialize(String id)throws Exception{if(failInitialize)throw new java.io.IOException("Simulated network interruption");}
        @Override public void updateGuide(String id){}
        @Override public JSONObject request(String method,String path,JSONObject body)throws Exception {
            assertFalse(body!=null&&body.toString().contains("VF_REPORT"));
            if(path.contains("values:batchUpdate")){
                batches++;JSONArray updates=body.getJSONArray("data");for(int i=0;i<updates.length();i++){JSONArray r=updates.getJSONObject(i).getJSONArray("values").getJSONArray(0);remote.add(Ledger.obj("event_id",r.getString(0),"entity_id",r.getString(1),"parents",r.getString(2),"kind",r.getString(3),"payload",r.getString(4),"checksum",r.getString(5)));}
            }
            return new JSONObject();
        }
        @Override public JSONArray values(String id,String range){return null;}
        @Override public void write(String id,String range,JSONArray rows){if(range.startsWith("Summary!"))summary=rows;}
    }
    @Test public void syncCalculatesLocallyAndReplayDoesNotDuplicate()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String database="isolated-cloud-storage-test.db";context.deleteDatabase(database);
        try(Store store=new Store(context,database)){
            store.set("sheet","test-table");store.edit(Ledger.obj("id","cash","entity","account","name","Cash","currency","RUB","account_type","cash"));store.edit(Ledger.transaction("income","cash","10.25","RUB","","","2026-01-01T00:00:00","RUB","1"));
            FakeCloud cloud=new FakeCloud(store.get("device",""));cloud.sync(store);assertEquals(0,store.pendingCount());assertEquals(2,cloud.remote.size());assertEquals(1,cloud.batches);
            assertTrue(cloud.summary.toString().contains("10.25"));String summary=cloud.summary.toString();cloud.sync(store);assertEquals(2,cloud.remote.size());assertEquals(1,cloud.batches);assertEquals(summary,cloud.summary.toString());
        }finally{context.deleteDatabase(database);}
    }
    @Test public void interruptedSetupReusesCandidateAndRepeatedCreateKeepsConnection()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String database="isolated-setup-retry-test.db";context.deleteDatabase(database);
        try(Store store=new Store(context,database)){
            FakeCloud cloud=new FakeCloud(store.get("device",""));SheetSetup setup=new SheetSetup(cloud){@Override public void verifyStorage(String id){}};
            cloud.failInitialize=true;assertThrows(java.io.IOException.class,()->setup.create(store));assertEquals("",store.get("sheet",""));assertEquals("candidate",store.get("setup_candidate",""));
            cloud.failInitialize=false;assertEquals("candidate",setup.create(store));assertEquals("candidate",setup.create(store));assertEquals(1,cloud.creations);assertEquals("true",store.get("cloud_test",""));
        }finally{context.deleteDatabase(database);}
    }
    @Test public void missingTableCanBeReplacedOnlyWhenLocalHistoryIsEmpty()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String database="isolated-missing-table-test.db";context.deleteDatabase(database);
        try(Store store=new Store(context,database)){
            store.set("sheet","gone");FakeCloud cloud=new FakeCloud(store.get("device",""));SheetSetup setup=new SheetSetup(cloud){@Override public void verifyStorage(String id){}};
            assertEquals("candidate",setup.create(store));assertEquals("gone",store.get("previous_sheet",""));assertEquals(1,cloud.creations);
            store.edit(Ledger.obj("id","cash","entity","account","currency","RUB"));store.set("sheet","gone");
            assertThrows(java.io.IOException.class,()->setup.create(store));assertEquals("gone",store.get("sheet",""));assertEquals(1,cloud.creations);assertEquals(1,store.events().size());
        }finally{context.deleteDatabase(database);}
    }
}
