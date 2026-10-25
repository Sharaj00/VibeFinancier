package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SheetSetupTest {
    private static class FakeCloud extends Cloud {
        boolean valid=true,deleted=false;JSONArray written;final List<String> paths=new ArrayList<>();
        FakeCloud(){super("not-a-token");}
        @Override public void verifySchema(String id){}
        @Override public JSONObject request(String method,String path,JSONObject body)throws Exception {
            paths.add(path);
            if(body!=null&&body.has("requests")){
                JSONObject request=body.getJSONArray("requests").getJSONObject(0);
                if(request.has("addSheet"))return Ledger.obj("replies",new JSONArray().put(Ledger.obj("addSheet",Ledger.obj("properties",Ledger.obj("sheetId",42)))));
                if(request.has("deleteSheet"))deleted=true;
            }
            if(method.equals("GET"))return Ledger.obj("sheets",new JSONArray().put(Ledger.obj("properties",Ledger.obj("sheetId",9,"title","J_00000000000000000000000000000001"))));
            return new JSONObject();
        }
        @Override public void write(String id,String range,JSONArray rows){written=rows;paths.add("/"+id+"/"+range);}
        @Override public JSONArray values(String id,String range){return valid?written:new JSONArray().put(new JSONArray(List.of("#NAME?")));}
    }
    private static JSONArray report(){return new JSONArray().put(new JSONArray(List.of("balance","probe.cash","Cash","RUB","9007199254741003.26"))).put(new JSONArray(List.of("balance","probe.ton","TON","TON","0.000000001"))).put(new JSONArray(List.of("income","","Income","RUB","9007199254741003.26")));}
    private SheetSetup setup(FakeCloud cloud){return new SheetSetup(cloud);}
    @Test public void probeWritesOnlySyntheticEventsAndCleansUp()throws Exception {
        FakeCloud cloud=new FakeCloud();setup(cloud).verifyStorage("private-copy");assertTrue(cloud.deleted);assertEquals(5,cloud.written.length());
        List<JSONObject> events=new ArrayList<>();for(int i=0;i<cloud.written.length();i++){JSONArray row=cloud.written.getJSONArray(i);events.add(Ledger.obj("event_id",row.getString(0),"entity_id",row.getString(1),"parents",row.getString(2),"kind",row.getString(3),"payload",row.getString(4),"checksum",row.getString(5)));}
        assertEquals("9007199254741003.26",Ledger.balances(Ledger.entities(events,false)).get("probe.cash").toPlainString());
        assertEquals("0.000000001",Ledger.balances(Ledger.entities(events,false)).get("probe.ton").toPlainString());
    }
    @Test public void alteredStorageFailsClosedAndStillCleansUp()throws Exception {
        FakeCloud cloud=new FakeCloud();cloud.valid=false;
        assertThrows(SheetSetup.StorageNotReady.class,()->setup(cloud).verifyStorage("private-copy"));assertTrue(cloud.deleted);
    }
    @Test public void roundedOrPartialResultsCannotUnlockSetup()throws Exception {
        JSONArray rows=report();assertTrue(SheetSetup.probeMatches(rows));rows.getJSONArray(0).put(4,"9007199254741004");assertFalse(SheetSetup.probeMatches(rows));
        assertFalse(SheetSetup.probeMatches(new JSONArray().put(report().getJSONArray(2))));assertFalse(SheetSetup.probeMatches(null));
    }
}
