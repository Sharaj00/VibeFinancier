package app.vibefinancier;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersistenceTest {
    private Context context;
    private final String database="isolated-persistence-test.db";
    @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(database);}
    @After public void after(){context.deleteDatabase(database);}
    private JSONObject bundle(){JSONObject p=Ledger.obj("id","source.cash","entity","account","currency","RUB","name","Test cash");return Ledger.obj("format","vibefinancier-import","version",1,"source_sha256","synthetic-fixture","entities",new JSONArray().put(p),"report",new JSONObject());}
    @Test public void offlineQueueSurvivesRestartAndReimportSkips()throws Exception{
        int slot;String id;
        try(Store s=new Store(context,database)){assertEquals(1,s.importBundle(bundle()));assertEquals(0,s.importBundle(bundle()));assertEquals(1,s.pendingCount());slot=s.pending().get(0).getInt("_slot");id=s.pending().get(0).getString("event_id");}
        try(Store s=new Store(context,database)){assertEquals(1,s.pendingCount());assertEquals(slot,s.pending().get(0).getInt("_slot"));assertEquals(id,s.pending().get(0).getString("event_id"));s.ack(id);assertEquals(0,s.pendingCount());}
    }
    @Test public void backupRoundTripAndTamperDetection()throws Exception{
        JSONObject backup;
        try(Store s=new Store(context,database)){s.importBundle(bundle());backup=s.backup();}
        context.deleteDatabase(database);
        try(Store s=new Store(context,database)){assertEquals(1,s.restore(backup));assertEquals(0,s.restore(backup));assertEquals("Test cash",s.entities(false).get(0).getString("name"));backup.put("content","{}");assertThrows(JSONException.class,()->s.restore(backup));assertEquals(1,s.events().size());}
    }
    @Test public void invalidBatchRollsBackCompletely()throws Exception{
        try(Store s=new Store(context,database)){JSONObject good=Ledger.event(Ledger.obj("id","a","entity","account","currency","RUB"),"","a");JSONObject invalid=Ledger.event(Ledger.obj("id","b","entity","account","currency","RUB"),"missing","b");assertThrows(JSONException.class,()->s.merge(java.util.List.of(good,invalid),true));assertEquals(0,s.events().size());assertEquals("2",s.get("next_slot",""));}
    }
}
