package app.vibefinancier;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Prepares private storage and checks exact round-trip values without any scripts. */
public class SheetSetup {
    private final Cloud cloud;
    public SheetSetup(Cloud cloud){this.cloud=cloud;}
    public static class StorageNotReady extends IOException {
        StorageNotReady(){super("The table did not preserve the synthetic test values exactly. No history was uploaded. Check access and retry.");}
    }
    public static String url(String id){return "https://docs.google.com/spreadsheets/d/"+id+"/edit";}
    public String create(Store store)throws Exception {
        String id=store.get("sheet","");
        if(!id.isEmpty()){
            try{cloud.verifySchema(id);verifyStorage(id);cloud.updateGuide(id);store.set("cloud_test","true");return id;}
            catch(Cloud.HttpFailure missing){
                if(missing.status!=404)throw missing;
                if(!store.events().isEmpty())throw new IOException("The connected table is unavailable. Your local history is preserved. Choose the same table with the Google account that owns it; automatic replacement is disabled while history exists.",missing);
                store.set("previous_sheet",id);id="";
            }
        }
        if(id.isEmpty()){
            id=store.get("setup_candidate","");
            if(id.isEmpty())id=cloud.create("VibeFinancier",created->store.set("setup_candidate",created));
            else cloud.initialize(id);
        }
        cloud.verifySchema(id);verifyStorage(id);cloud.updateGuide(id);
        store.set("sheet",id);store.set("cloud_test","true");store.set("setup_candidate","");store.set("setup_tag","");store.set("sync_error","");
        return id;
    }
    public void connect(Store store,String id)throws Exception {
        if(id.equals(store.get("cloud_test_sheet","")))throw new IOException("This is the synthetic test table. Choose Create private table for your real ledger.");
        String current=store.get("sheet","");
        if(!current.equals(id)&&!store.events().isEmpty())throw new IOException("A local history already exists. Export a backup before connecting a different ledger.");
        if(!current.isEmpty()&&!current.equals(id)){
            try{if(!cloud.readEvents(current,cloud.journals(current)).isEmpty())throw new IOException("A different history is already connected. It was preserved; changing profiles needs an explicit migration.");}
            catch(Cloud.HttpFailure missing){if(missing.status!=404)throw missing;}
        }
        cloud.verifySchema(id);verifyStorage(id);
        List<JSONObject> events=cloud.readEvents(id,cloud.journals(id));
        String base="RUB";JSONArray config=cloud.values(id,"Config!B2");if(config!=null&&config.length()>0)base=config.getJSONArray(0).getString(0);Money.scale(base);
        cloud.updateGuide(id);store.merge(events,false);store.set("base",base);store.set("sheet",id);store.set("cloud_test","true");store.set("cloud_summary","[]");store.set("cloud_filters","[]");store.set("sync_error","");
    }
    /** A temporary non-journal tab verifies repeat writes and exact persisted strings. */
    public void verifyStorage(String id)throws Exception {
        cloud.verifySchema(id);String tab="VF_Check_"+UUID.randomUUID().toString().replace("-","");
        JSONObject result=cloud.request("POST","/"+id+":batchUpdate",Ledger.obj("requests",new JSONArray().put(Ledger.obj("addSheet",Ledger.obj("properties",Ledger.obj("title",tab,"gridProperties",Ledger.obj("rowCount",8,"columnCount",6)))))));
        int sheetId=result.getJSONArray("replies").getJSONObject(0).getJSONObject("addSheet").getJSONObject("properties").getInt("sheetId");
        Exception failed=null;
        try {
            JSONArray expected=probeRows();cloud.write(id,tab+"!A1:F5",expected);cloud.write(id,tab+"!A1:F5",expected);
            JSONArray actual=cloud.values(id,tab+"!A1:F8");
            if(actual==null||!actual.toString().equals(expected.toString()))throw new StorageNotReady();
            List<JSONObject> events=new ArrayList<>();
            for(int i=0;i<actual.length();i++){JSONArray r=actual.getJSONArray(i);events.add(Ledger.obj("event_id",r.getString(0),"entity_id",r.getString(1),"parents",r.getString(2),"kind",r.getString(3),"payload",r.getString(4),"checksum",r.getString(5)));}
            if(!probeMatches(LedgerReport.calculate(events,"RUB","0000-01-01","9999-12-31","","")))throw new StorageNotReady();
        }catch(Exception e){failed=e;throw e;}
        finally{try{cloud.request("POST","/"+id+":batchUpdate",Ledger.obj("requests",new JSONArray().put(Ledger.obj("deleteSheet",Ledger.obj("sheetId",sheetId)))));}catch(Exception cleanup){if(failed!=null)failed.addSuppressed(cleanup);else throw new IOException("Storage check passed, but temporary check cleanup failed. Retry setup; no history was uploaded.",cleanup);}}
    }
    public static JSONArray probeRows()throws Exception {
        JSONArray rows=new JSONArray();
        rows.put(Cloud.row(Ledger.event(Ledger.obj("id","probe.cash","entity","account","name","Setup check","currency","RUB","account_type","cash"),"","probe.account")));
        rows.put(Cloud.row(Ledger.event(Ledger.obj("id","probe.ton","entity","account","name","Precision check","currency","TON","account_type","wallet"),"","probe.ton.account")));
        String[] amounts={"10.25","9007199254740993.01","0.000000001"};
        for(int i=0;i<amounts.length;i++){String currency=i==2?"TON":"RUB";JSONObject tx=Ledger.transaction("income",i==2?"probe.ton":"probe.cash",amounts[i],currency,"","Synthetic setup check","2026-01-01T00:00:00","RUB","1");tx.put("id","probe.tx."+i);rows.put(Cloud.row(Ledger.event(tx,"","probe.event."+i)));}
        return rows;
    }
    public static boolean probeMatches(JSONArray rows){
        boolean cash=false,ton=false,income=false;
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONArray r=rows.optJSONArray(i);if(r==null)continue;
            if(r.optString(0).equals("balance")&&r.optString(1).equals("probe.cash")&&r.optString(4).equals("9007199254741003.26"))cash=true;
            if(r.optString(0).equals("balance")&&r.optString(1).equals("probe.ton")&&r.optString(4).equals("0.000000001"))ton=true;
            if(r.optString(0).equals("income")&&r.optString(4).equals("9007199254741003.26"))income=true;
        }
        return cash&&ton&&income;
    }
}
