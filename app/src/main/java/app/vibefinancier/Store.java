package app.vibefinancier;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class Store extends SQLiteOpenHelper {
    private long fullVersion=-1,metadataVersion=-1;private volatile long snapshotToken;
    private volatile List<String> fullCache;private List<String> metadataCache;
    public Store(Context context) { this(context,"ledger.db"); }
    Store(Context context,String database) { super(context,database,null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, entity TEXT NOT NULL, payload TEXT NOT NULL, slot INTEGER UNIQUE, pending INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("INSERT INTO meta VALUES('device',?)",new Object[]{UUID.randomUUID().toString().replace("-","")});
        db.execSQL("INSERT INTO meta VALUES('next_slot','2')");
        db.execSQL("CREATE TABLE picker_entities(id TEXT PRIMARY KEY)");db.execSQL("CREATE INDEX events_by_entity ON events(entity)");db.execSQL("INSERT INTO meta VALUES('picker_index_ready','1')");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Unsupported database version"); }
    public synchronized String get(String key,String fallback) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})) { return c.moveToFirst()?c.getString(0):fallback; }
    }
    public synchronized void set(String key,String value) { ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    public synchronized List<JSONObject> events() throws JSONException {
        List<JSONObject> result=new ArrayList<>(); try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM events ORDER BY rowid",null)) {
            while(c.moveToNext()) result.add(new JSONObject(c.getString(0))); } return result;
    }
    private long dataVersion(){try(Cursor c=getReadableDatabase().rawQuery("PRAGMA data_version",null)){c.moveToFirst();return c.getLong(0);}}
    synchronized long resolvedToken(){return fullCache!=null&&fullVersion==dataVersion()?snapshotToken:-1;}
    long fastToken(){return fullCache==null?-1:snapshotToken;}
    void invalidate(){fullCache=null;}
    private static List<JSONObject> copy(List<String> cache,Boolean deleted)throws JSONException {List<JSONObject> result=new ArrayList<>();for(String text:cache){JSONObject p=new JSONObject(text);if(deleted==null||p.optBoolean("deleted")==deleted)result.add(p);}return result;}
    private static List<String> strings(List<JSONObject> values){List<String> result=new ArrayList<>();for(JSONObject p:values)result.add(p.toString());return result;}
    public synchronized List<JSONObject> entities(boolean deleted) throws JSONException {
        long version=dataVersion();if(fullCache==null||fullVersion!=version){
            fullCache=strings(Ledger.allEntities(events()));fullVersion=version;snapshotToken++;
        }
        return copy(fullCache,deleted);
    }
    /** Resolve only picker entities and their complete revision chains, without hashing transaction history. */
    synchronized List<JSONObject> metadata() throws JSONException {
        long version=dataVersion();if(metadataCache==null||metadataVersion!=version){
            SQLiteDatabase db=getWritableDatabase();if(!get("picker_index_ready","").equals("1")){db.beginTransaction();try{db.execSQL("CREATE TABLE IF NOT EXISTS picker_entities(id TEXT PRIMARY KEY)");db.execSQL("CREATE INDEX IF NOT EXISTS events_by_entity ON events(entity)");db.execSQL("INSERT OR IGNORE INTO picker_entities SELECT DISTINCT entity FROM events WHERE payload LIKE ? OR payload LIKE ? OR payload LIKE ?",new Object[]{"%\"kind\":\"account\"%","%\"kind\":\"category\"%","%\"kind\":\"rate\"%"});set("picker_index_ready","1");db.setTransactionSuccessful();}finally{db.endTransaction();}}
            List<JSONObject> selected=new ArrayList<>();String sql="SELECT payload FROM events WHERE entity IN (SELECT id FROM picker_entities) ORDER BY rowid";
            try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext())selected.add(new JSONObject(c.getString(0)));}
            List<JSONObject> result=new ArrayList<>();for(JSONObject p:Ledger.entities(selected,false))if(Set.of("account","category","rate").contains(p.optString("entity")))result.add(p);metadataCache=strings(result);metadataVersion=version;
        }
        return copy(metadataCache,null);
    }
    public synchronized List<JSONObject> type(String type,boolean archived) throws JSONException {
        List<JSONObject> out=new ArrayList<>();for(JSONObject p:Set.of("account","category","rate").contains(type)?metadata():entities(false)) if(p.optString("entity").equals(type)&&(archived||!p.optBoolean("archived"))) out.add(p);return out;
    }
    public synchronized int pendingCount() { try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM events WHERE pending=1",null)){c.moveToFirst();return c.getInt(0);} }
    public synchronized List<JSONObject> pending() throws JSONException {
        List<JSONObject> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload,slot FROM events WHERE pending=1 ORDER BY slot LIMIT 200",null)) {
            while(c.moveToNext()){JSONObject e=new JSONObject(c.getString(0));e.put("_slot",c.getInt(1));out.add(e);}}
        return out;
    }
    public synchronized void ack(String id) {getWritableDatabase().execSQL("UPDATE events SET pending=0 WHERE id=?",new Object[]{id});}
    public synchronized JSONObject edit(JSONObject p) throws JSONException {
        String parent=p.optString("_head");p.remove("_head");return resolve(p,parent);
    }
    /** Transaction and receipt commit together. Stable transaction identity also deduplicates devices. */
    synchronized boolean confirmPayment(JSONObject plan,String date,JSONObject transaction) throws JSONException {
        String receipt=Payments.occurrence(plan,java.time.LocalDate.parse(date));
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            invalidate();List<JSONObject> values=entities(false);
            if(Ledger.heads(events()).containsKey(receipt))return false;
            JSONObject current=null;for(JSONObject p:values)if(p.optString("id").equals(plan.optString("id")))current=p;
            if(current==null||current.optBoolean("archived")||!current.optString("_head").equals(plan.optString("_head")))throw new JSONException("Payment plan changed. Reopen it before confirming.");
            if(!Payments.dates(current,java.time.LocalDate.parse(date),java.time.LocalDate.parse(date)).contains(java.time.LocalDate.parse(date)))throw new JSONException("Invalid payment date.");
            if(transaction!=null){JSONObject source=null;for(JSONObject p:values)if(p.optString("id").equals(transaction.optString("account")))source=p;
                if(source==null||source.optBoolean("archived")||!source.optString("currency").equals(transaction.optString("currency")))throw new JSONException("Source account is unavailable or its currency changed.");
                JSONArray postings=transaction.getJSONArray("postings");for(int i=0;i<postings.length();i++){JSONObject leg=postings.getJSONObject(i),account=null;for(JSONObject p:values)if(p.optString("entity").equals("account")&&p.optString("id").equals(leg.optString("account")))account=p;
                    if(account==null||account.optBoolean("archived")||!account.optString("currency").equals(leg.optString("currency")))throw new JSONException("A payment account is unavailable or its currency changed.");
                }
            }
            JSONObject marker=Ledger.obj("id",receipt,"entity","payment_receipt","plan",plan.optString("id"),"date",date);
            List<JSONObject> events=new ArrayList<>();
            if(transaction!=null){transaction.remove("_head");transaction.remove("_planned");transaction.put("id",receipt+".transaction");transaction.put("payment_plan",plan.optString("id"));transaction.put("payment_date",date);events.add(Ledger.event(transaction,"",UUID.randomUUID().toString()));}
            events.add(Ledger.event(marker,"",receipt+".receipt"));merge(events,true);db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();invalidate();}
    }
    public synchronized JSONObject resolve(JSONObject p,String parents) throws JSONException {
        JSONObject e=Ledger.event(p,parents,UUID.randomUUID().toString());merge(List.of(e),true);return e;
    }
    public synchronized int merge(List<JSONObject> incoming,boolean pending) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int added=0;
        try {
            List<JSONObject> all=events();all.addAll(incoming);Ledger.heads(all);
            for(JSONObject e:incoming) {
                Ledger.validateEvent(e);String id=e.getString("event_id");
                try(Cursor c=db.rawQuery("SELECT payload FROM events WHERE id=?",new String[]{id})) {
                    if(c.moveToFirst()) {
                        if(!new JSONObject(c.getString(0)).getString("checksum").equals(e.getString("checksum")))throw new JSONException("Conflicting event ID");
                        continue;
                    }
                }
                JSONObject clean=new JSONObject(e.toString());clean.remove("_slot");ContentValues v=new ContentValues();v.put("id",id);v.put("entity",e.getString("entity_id"));v.put("payload",clean.toString());v.put("pending",pending?1:0);
                if(pending) {int slot=Integer.parseInt(get("next_slot","2"));v.put("slot",slot);set("next_slot",Integer.toString(slot+1));}
                db.insertOrThrow("events",null,v);added++;
                if(Set.of("account","category","rate").contains(e.optString("kind"))&&get("picker_index_ready","").equals("1"))db.execSQL("INSERT OR IGNORE INTO picker_entities(id) VALUES(?)",new Object[]{e.getString("entity_id")});
            }
            db.setTransactionSuccessful();return added;
        } finally {db.endTransaction();if(added>0){fullCache=null;metadataCache=null;}}
    }
    public synchronized int importBundle(JSONObject bundle) throws JSONException {
        if(!bundle.optString("format").equals("vibefinancier-import")||bundle.optInt("version")!=1)throw new JSONException("Unsupported import format");
        JSONArray entities=bundle.getJSONArray("entities");List<JSONObject> es=new ArrayList<>();String source=bundle.getString("source_sha256");
        for(int i=0;i<entities.length();i++) {JSONObject p=entities.getJSONObject(i);p.remove("_head");es.add(Ledger.event(p,"","import."+Ledger.hash(source+"\n"+p.getString("id"))));}
        int added=merge(es,true);set("real_import","true");set("last_import_report",bundle.optJSONObject("report")==null?"{}":bundle.getJSONObject("report").toString());return added;
    }
    public synchronized JSONObject backup() throws JSONException {
        JSONObject content=Ledger.obj("version",1,"events",new JSONArray(events()),"base_currency",get("base","RUB"),"import_report",get("last_import_report","{}"));
        String data=content.toString();return Ledger.obj("format","vibefinancier-backup","version",1,"content",data,"sha256",Ledger.hash(data));
    }
    public synchronized int restore(JSONObject wrapper) throws JSONException {
        if(!wrapper.optString("format").equals("vibefinancier-backup")||wrapper.optInt("version")!=1)throw new JSONException("Unsupported backup format");
        String text=wrapper.getString("content");if(!Ledger.hash(text).equals(wrapper.getString("sha256")))throw new JSONException("Backup checksum mismatch");
        JSONObject content=new JSONObject(text);if(content.getInt("version")!=1)throw new JSONException("Unsupported content version");
        JSONArray list=content.getJSONArray("events");List<JSONObject> es=new ArrayList<>();for(int i=0;i<list.length();i++)es.add(list.getJSONObject(i));
        int count=merge(es,true);set("base",content.optString("base_currency","RUB"));set("last_import_report",content.optString("import_report","{}"));set("real_import","true");return count;
    }
}
