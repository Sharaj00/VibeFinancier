package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class Ledger {
    private Ledger() {}
    public static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); String hex="0123456789abcdef"; for (byte v : bytes) {b.append(hex.charAt((v>>>4)&15));b.append(hex.charAt(v&15));}
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static JSONObject obj(Object... pairs) {
        JSONObject o = new JSONObject();
        try { for (int i=0;i<pairs.length;i+=2) o.put(pairs[i].toString(),pairs[i+1]); }
        catch (JSONException e) { throw new IllegalArgumentException(e); } return o;
    }
    public static JSONObject event(JSONObject payload, String parents, String eventId) {
        String text=payload.toString();
        return obj("event_id",eventId,"entity_id",payload.optString("id"),"parents",parents,"kind",payload.optString("entity"),"payload",text,
                "checksum",hash(eventId+"\n"+payload.optString("id")+"\n"+parents+"\n"+payload.optString("entity")+"\n"+text));
    }
    public static void validateEvent(JSONObject e) throws JSONException {
        String id=e.getString("event_id"), entity=e.getString("entity_id"), parents=e.getString("parents"),kind=e.getString("kind"), text=e.getString("payload");
        if (!hash(id+"\n"+entity+"\n"+parents+"\n"+kind+"\n"+text).equals(e.getString("checksum"))) throw new JSONException("Event checksum mismatch");
        JSONObject p=new JSONObject(text);
        if (!entity.equals(p.getString("id")) || !kind.equals(p.getString("entity"))) throw new JSONException("Entity identity mismatch");
        if (text.length()>45000 || entity.isBlank() || id.isBlank()) throw new JSONException("Invalid event");
    }
    public static Map<String,List<JSONObject>> heads(List<JSONObject> events) throws JSONException {
        Map<String,JSONObject> unique=new LinkedHashMap<>(); Set<String> referenced=new HashSet<>();
        for(JSONObject e:events) {
            validateEvent(e); String id=e.getString("event_id");
            if(unique.containsKey(id) && !unique.get(id).getString("checksum").equals(e.getString("checksum"))) throw new JSONException("Event ID collision");
            unique.put(id,e);
        }
        for(JSONObject e:unique.values()) for(String parent:e.getString("parents").split(",")) {
            if(parent.isEmpty()) continue;
            if(!unique.containsKey(parent) || !unique.get(parent).getString("entity_id").equals(e.getString("entity_id"))) throw new JSONException("Missing or invalid parent event");
            referenced.add(parent);
        }
        Set<String> visited=new HashSet<>(),visiting=new HashSet<>();
        for(String id:unique.keySet()) visit(id,unique,visited,visiting);
        Map<String,List<JSONObject>> result=new LinkedHashMap<>();
        for(JSONObject e:unique.values()) if(!referenced.contains(e.getString("event_id"))) result.computeIfAbsent(e.getString("entity_id"),k->new ArrayList<>()).add(e);
        for(JSONObject e:unique.values()) if(!result.containsKey(e.getString("entity_id"))) throw new JSONException("Revision cycle detected");
        return result;
    }
    private static void visit(String id,Map<String,JSONObject> all,Set<String> visited,Set<String> visiting) throws JSONException {
        if(visited.contains(id))return;
        if(!visiting.add(id))throw new JSONException("Revision cycle detected");
        for(String parent:all.get(id).getString("parents").split(","))if(!parent.isEmpty())visit(parent,all,visited,visiting);
        visiting.remove(id);visited.add(id);
    }
    public static List<JSONObject> entities(List<JSONObject> events, boolean deleted) throws JSONException {
        return resolvedEntities(events,deleted);
    }
    static List<JSONObject> allEntities(List<JSONObject> events)throws JSONException{return resolvedEntities(events,null);}
    private static List<JSONObject> resolvedEntities(List<JSONObject> events,Boolean deleted)throws JSONException {
        List<JSONObject> result=new ArrayList<>();
        for(List<JSONObject> heads:heads(events).values()) if(heads.size()==1) {
            JSONObject e=heads.get(0), p=new JSONObject(e.getString("payload"));
            p.put("_head",e.getString("event_id"));
            if(deleted==null||p.optBoolean("deleted")==deleted) result.add(p);
        } return result;
    }
    public static Map<String,BigDecimal> balances(List<JSONObject> entities) throws JSONException {
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        for(JSONObject p:entities) if(p.optString("entity").equals("account")) result.put(p.getString("id"),BigDecimal.ZERO);
        for(JSONObject p:entities) if(p.optString("entity").equals("transaction")) {
            JSONArray legs=p.optJSONArray("postings"); if(legs==null) continue;
            for(int i=0;i<legs.length();i++) {JSONObject l=legs.getJSONObject(i);String account=l.getString("account");
                if(result.containsKey(account)) result.put(account,result.get(account).add(Money.decimal(l.getString("amount"))));}
        } return result;
    }
    public static JSONObject transaction(String kind,String account,String amount,String currency,String category,String note,String time,String base,String rate) {
        String id=UUID.randomUUID().toString(); BigDecimal d=Money.decimal(amount);
        if(d.signum()<=0) throw new IllegalArgumentException("Amount must be greater than zero.");
        boolean expense=kind.equals("expense");
        String signed=expense?Money.plain(d.negate()):Money.plain(d);
        JSONObject p=obj("id",id,"entity","transaction","kind",kind,"account",account,"currency",currency,"amount",amount,"category",category,"note",note,"time",time,
            "base_currency",base,"rate",rate,"base_amount",Money.convert(amount,rate,Money.scale(base)),"rate_source",currency.equals(base)?"identity":"manual","rate_time",time,
            "postings",new JSONArray().put(obj("account",account,"amount",signed,"currency",currency)));
        return p;
    }
}
