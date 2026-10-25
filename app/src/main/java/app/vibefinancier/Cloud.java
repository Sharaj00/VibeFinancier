package app.vibefinancier;

import android.content.Context;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Cloud {
    public static class HttpFailure extends IOException {
        public final int status;
        HttpFailure(String service,int status,String operation){super(service+" HTTP "+status+" · "+operation+(status==404?". The table is unavailable to the selected Google account or no longer exists.":status==403?". Check enabled APIs and permission to this file.":status==401?". Reconnect Google.":". Retry safely."));this.status=status;}
    }
    public static final String SCOPE="https://www.googleapis.com/auth/drive.file";
    private final String token;
    private final java.util.function.Consumer<String> progress;
    private int requests;
    public Cloud(String token) { this(token,message->{}); }
    public Cloud(String token,java.util.function.Consumer<String> progress) { this.token=token;this.progress=progress; }
    public static AuthorizationRequest authorization() { return AuthorizationRequest.builder().setRequestedScopes(List.of(new Scope(SCOPE))).build(); }
    public static String backgroundToken(Context c) throws Exception {
        AuthorizationResult r=Tasks.await(Identity.getAuthorizationClient(c).authorize(authorization()),30,TimeUnit.SECONDS);
        if(r.hasResolution())throw new IOException("Open the app and reconnect Google.");return r.getAccessToken();
    }
    public JSONObject request(String method,String path,JSONObject body) throws Exception {
        return serviceRequest("Google Sheets","https://sheets.googleapis.com/v4/spreadsheets",method,path,body);
    }
    private JSONObject serviceRequest(String service,String base,String method,String path,JSONObject body) throws Exception {
        String operation=path.contains("/values/")||path.contains("/values:")?"table cells":path.contains(":batchUpdate")?"table structure":path.isEmpty()?"create table":"table metadata";
        progress.accept(service+" request "+(++requests)+" ("+method+") · "+operation);
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        try {
            c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();if(status<200||status>=300)throw new HttpFailure(service,status,operation);
            if(status==204)return new JSONObject();
            try(InputStream in=c.getInputStream()){return new JSONObject(new String(Streams.read(in,20_000_000),StandardCharsets.UTF_8));}
        }finally{c.disconnect();}
    }
    private static String encoded(String text){return android.net.Uri.encode(text);}
    public JSONArray values(String id,String range) throws Exception {return request("GET","/"+id+"/values/"+encoded(range)+"?valueRenderOption=UNFORMATTED_VALUE",null).optJSONArray("values");}
    public void write(String id,String range,JSONArray rows) throws Exception {request("PUT","/"+id+"/values/"+encoded(range)+"?valueInputOption=RAW",Ledger.obj("range",range,"majorDimension","ROWS","values",rows));}
    public static JSONArray headers(){return new JSONArray(List.of("event_id","entity_id","parents","kind","payload","checksum"));}
    public static JSONArray row(JSONObject e) throws JSONException {return new JSONArray(List.of(e.getString("event_id"),e.getString("entity_id"),e.getString("parents"),e.getString("kind"),e.getString("payload"),e.getString("checksum")));}
    public String create(String title) throws Exception { return create(title,id->{}); }
    public String create(String title,java.util.function.Consumer<String> created) throws Exception {
        JSONArray tabs=new JSONArray();for(String name:List.of("Config","Summary","Rates","Guide"))tabs.put(Ledger.obj("properties",Ledger.obj("title",name,"gridProperties",Ledger.obj("rowCount",1000,"columnCount",12))));
        JSONObject response=request("POST","",Ledger.obj("properties",Ledger.obj("title",title,"locale","en_US","timeZone","Asia/Omsk"),"sheets",tabs));String id=response.getString("spreadsheetId");
        created.accept(id);initialize(id);return id;
    }
    public void initialize(String id)throws Exception {
        write(id,"Config!A1:B6",new JSONArray().put(new JSONArray(List.of("schema","vibefinancier/1"))).put(new JSONArray(List.of("base_currency","RUB"))).put(new JSONArray(List.of("from_date","0000-01-01"))).put(new JSONArray(List.of("to_date","9999-12-31"))).put(new JSONArray(List.of("account_filter",""))).put(new JSONArray(List.of("category_filter",""))));
        write(id,"Rates!A1:F1",new JSONArray().put(new JSONArray(List.of("currency","base","rate","source","updated_at","manual"))));
        updateGuide(id);
    }
    public void updateGuide(String id)throws Exception {
        write(id,"Guide!A1:B4",new JSONArray().put(new JSONArray(List.of("VibeFinancier","Schema 1 · calculations on device"))).put(new JSONArray(List.of("Setup","Ready to use. No Apps Script or manual setup is required."))).put(new JSONArray(List.of("Editing","Use the app. Journal cells are immutable. Rates and Config filters may be edited as text."))).put(new JSONArray(List.of("Privacy","Keep this workbook private. Summary contains a snapshot calculated by the app."))));
    }
    public void verifySchema(String id) throws Exception {JSONArray rows=values(id,"Config!A1:B1");if(rows==null||rows.length()==0||!rows.getJSONArray(0).optString(1).equals("vibefinancier/1"))throw new IOException("This is not a compatible VibeFinancier table.");}
    public List<String> journals(String id) throws Exception {
        JSONArray tabs=request("GET","/"+id+"?fields=sheets(properties(title))",null).getJSONArray("sheets");List<String> out=new ArrayList<>();
        for(int i=0;i<tabs.length();i++){String n=tabs.getJSONObject(i).getJSONObject("properties").getString("title");if(n.matches("J_[a-f0-9]{32}"))out.add(n);}return out;
    }
    public List<JSONObject> readEvents(String id,List<String> tabs) throws Exception {
        List<JSONObject> out=new ArrayList<>();
        for(String tab:tabs){JSONArray rows=values(id,tab+"!A2:F20000");if(rows==null)continue;
            for(int i=0;i<rows.length();i++){JSONArray r=rows.getJSONArray(i);if(r.length()==0||r.optString(0).isEmpty())continue;if(r.length()!=6)throw new IOException("Journal row is incomplete; repair from backup.");
                JSONObject e=Ledger.obj("event_id",r.getString(0),"entity_id",r.getString(1),"parents",r.getString(2),"kind",r.getString(3),"payload",r.getString(4),"checksum",r.getString(5));Ledger.validateEvent(e);out.add(e);}}
        Ledger.heads(out);return out;
    }
    public void sync(Store store) throws Exception {
        String id=store.get("sheet","");if(id.isEmpty())throw new IOException("Connect a Google table first.");verifySchema(id);
        if(store.get("real_import","false").equals("true")&&!store.get("cloud_test","false").equals("true"))throw new IOException("Run the separate cloud test before uploading imported history.");
        List<String> tabs=journals(id);String own="J_"+store.get("device","");
        if(!tabs.contains(own)) {request("POST","/"+id+":batchUpdate",Ledger.obj("requests",new JSONArray().put(Ledger.obj("addSheet",Ledger.obj("properties",Ledger.obj("title",own,"gridProperties",Ledger.obj("rowCount",20000,"columnCount",6)))))));write(id,own+"!A1:F1",new JSONArray().put(headers()));tabs.add(own);}
        List<JSONObject> remote=readEvents(id,tabs);Map<String,String> known=new HashMap<>();for(JSONObject e:remote)known.put(e.getString("event_id"),e.getString("checksum"));store.merge(remote,false);
        JSONArray tableRates=values(id,"Rates!A2:F100");
        if(tableRates!=null)for(int i=0;i<tableRates.length();i++){
            JSONArray r=tableRates.getJSONArray(i);if(!r.optString(1).equals(store.get("base","RUB"))||!r.optString(5).equalsIgnoreCase("true"))continue;
            String currency=r.getString(0),rate=r.getString(2),rateId="rate."+currency+"."+r.getString(1);Money.scale(currency);if(Money.decimal(rate).signum()<=0)throw new IOException("Manual table rate must be positive");
            JSONObject previous=null;for(JSONObject p:store.type("rate",true))if(p.optString("id").equals(rateId))previous=p;
            if(previous==null||!previous.optString("rate").equals(rate)||!previous.optString("asof").equals(r.optString(4))){JSONObject p=Ledger.obj("id",rateId,"entity","rate","currency",currency,"base",r.getString(1),"rate",rate,"source",r.optString(3,"manual table"),"asof",r.optString(4),"manual",true);if(previous!=null)p.put("_head",previous.getString("_head"));store.edit(p);}
        }
        JSONArray ownRows=values(id,own+"!A2:F20000");
        for(int batch=0;batch<20;batch++) {
        List<JSONObject> pending=store.pending();JSONArray updates=new JSONArray();
        if(pending.isEmpty())break;
        for(JSONObject e:pending){String event=e.getString("event_id");if(known.containsKey(event)) {if(!known.get(event).equals(e.getString("checksum")))throw new IOException("Cloud event collision");store.ack(event);continue;}
            int slot=e.getInt("_slot");if(slot>20000)throw new IOException("Journal is full. Export a backup before extending capacity.");
            JSONArray r=ownRows==null?null:ownRows.optJSONArray(slot-2);
            if(r!=null&&r.length()>0&&!r.optString(0).isEmpty()){
                if(!r.optString(0).equals(event)||!r.optString(5).equals(e.getString("checksum")))throw new IOException("Fixed journal row was modified; sync stopped.");}
            updates.put(Ledger.obj("range",own+"!A"+slot+":F"+slot,"values",new JSONArray().put(row(e))));}
        if(updates.length()>0)request("POST","/"+id+"/values:batchUpdate",Ledger.obj("valueInputOption","RAW","data",updates));
        for(JSONObject e:pending)store.ack(e.getString("event_id"));
        }
        write(id,"Config!B2",new JSONArray().put(new JSONArray().put(store.get("base","RUB"))));
        JSONArray filters=values(id,"Config!B3:B6");
        String from=filter(filters,0,"0000-01-01"),to=filter(filters,1,"9999-12-31"),account=filter(filters,2,""),category=filter(filters,3,"");
        JSONArray summary=LedgerReport.calculate(store.events(),store.get("base","RUB"),from,to,account,category);
        // Summary is an optional device-generated snapshot, never an authority for balances.
        request("POST","/"+id+"/values/"+encoded("Summary!A:F")+":clear",new JSONObject());
        write(id,"Summary!A1:F"+summary.length(),summary);
        store.set("cloud_summary",summary.toString());store.set("cloud_filters",filters==null?"[]":filters.toString());store.set("last_sync",java.time.Instant.now().toString());store.set("sync_error","");
    }
    private static String filter(JSONArray rows,int index,String fallback){JSONArray row=rows==null?null:rows.optJSONArray(index);return row==null||row.optString(0).isEmpty()?fallback:row.optString(0);}

    public void calculateView(Store store,String from,String to,String account,String category)throws Exception {
        String id=store.get("sheet","");if(id.isEmpty())throw new IOException("Connect a table first.");
        write(id,"Config!B3:B6",new JSONArray().put(new JSONArray().put(from)).put(new JSONArray().put(to)).put(new JSONArray().put(account)).put(new JSONArray().put(category)));sync(store);
    }
    public String transportTest(Store store) throws Exception {
        String id=create("VibeFinancier - isolated sync test");String tab="J_"+UUID.randomUUID().toString().replace("-","");
        request("POST","/"+id+":batchUpdate",Ledger.obj("requests",new JSONArray().put(Ledger.obj("addSheet",Ledger.obj("properties",Ledger.obj("title",tab,"gridProperties",Ledger.obj("rowCount",20000,"columnCount",6)))))));
        JSONObject account=Ledger.event(Ledger.obj("id","test.cash","entity","account","name","Test cash","currency","RUB","account_type","cash"),"","test.account");
        JSONObject tx=Ledger.transaction("income","test.cash","10.25","RUB","","Synthetic test", "2026-01-01T00:00:00","RUB","1");tx.put("id","test.income");JSONObject event=Ledger.event(tx,"","test.income.event");
        JSONArray rows=new JSONArray().put(headers()).put(row(account)).put(row(event));write(id,tab+"!A1:F3",rows);write(id,tab+"!A1:F3",rows);
        List<JSONObject> found=readEvents(id,List.of(tab));if(found.size()!=2||!Ledger.balances(Ledger.entities(found,false)).get("test.cash").toPlainString().equals("10.25"))throw new IOException("Cloud transport test failed");
        store.set("cloud_test_sheet",id);store.set("transport_test","true");return id;
    }
    public void verifyCalculationTest(Store store) throws Exception {
        String id=store.get("cloud_test_sheet","");if(id.isEmpty())throw new IOException("Run the cloud transport test first.");
        new SheetSetup(this).verifyStorage(id);store.set("cloud_test","true");
    }
}
