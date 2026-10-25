package app.vibefinancier;

import org.json.*;
import java.time.*;
import java.util.*;

/** A reviewable repair plan. No financial postings or execution cross-rate is rewritten. */
final class RateRepair {
    interface Provider {JSONObject get(String currency,String base,LocalDate date)throws Exception;}
    private interface OperationProvider {JSONObject get(JSONObject operation)throws Exception;}
    final List<JSONObject> replacements=new ArrayList<>();final List<String> skipped=new ArrayList<>();
    RateRepair(List<JSONObject> values,Provider provider)throws Exception {
        this(values,p->provider.get(p.getString("currency"),p.getString("base_currency"),LocalDate.parse(p.getString("time").substring(0,10))),false);
    }
    static RateRepair prepare(List<JSONObject> values)throws Exception{Rates.HistoricalBatch batch=new Rates.HistoricalBatch(values);return new RateRepair(values,batch::get,true);}
    private RateRepair(List<JSONObject> values,OperationProvider provider,boolean intraday)throws Exception {
        Map<String,JSONObject> cache=new HashMap<>();Map<String,String> failures=new HashMap<>();
        for(JSONObject p:values)if(p.optString("entity").equals("transaction")&&Rates.CRYPTO.containsKey(p.optString("currency"))&&!p.optString("currency").equals(p.optString("base_currency"))){
            try{String date=p.getString("time").substring(0,10),cur=p.getString("currency"),base=p.getString("base_currency"),key=cur+base+(intraday?p.getString("time")+p.optString("timezone"):date);if(failures.containsKey("provider"))throw new IllegalArgumentException(failures.get("provider"));if(failures.containsKey(key))throw new IllegalArgumentException(failures.get(key));JSONObject q=cache.get(key);if(q==null){try{q=provider.get(p);cache.put(key,q);}catch(Exception e){String reason=e.getMessage()==null?"Historical provider unavailable":e.getMessage();failures.put(key,reason);if(reason.contains("HTTP 429")||reason.contains("HTTP 401"))failures.put("provider",reason);throw e;}}
                JSONObject next=apply(p,q);if(!next.optString("rate").equals(p.optString("rate"))||!next.optString("rate_source").equals(p.optString("rate_source")))replacements.add(next);
            }catch(Exception e){skipped.add(p.optString("id")+" · "+p.optString("time")+" · "+e.getMessage());}
        }
    }
    static JSONObject apply(JSONObject p,JSONObject q)throws Exception {
        if(!p.optString("currency").equals(q.optString("currency"))||!p.optString("base_currency").equals(q.optString("base")))throw new IllegalArgumentException("Historical pair mismatch.");
        JSONObject next=new JSONObject(p.toString());if(!next.has("rate_before_repair"))next.put("rate_before_repair",Ledger.obj("rate",p.optString("rate"),"base_amount",p.optString("base_amount"),"fee_base",p.optString("fee_base"),"rate_source",p.optString("rate_source"),"rate_time",p.optString("rate_time")));
        next.put("rate",q.getString("rate"));next.put("base_amount",Money.convert(p.getString("amount"),q.getString("rate"),Money.scale(p.getString("base_currency"))));if(p.has("fee"))next.put("fee_base",Money.convert(p.getString("fee"),q.getString("rate"),Money.scale(p.getString("base_currency"))));next.put("rate_source",q.optString("source"));next.put("rate_time",q.optString("asof"));next.put("rate_granularity",q.optString("granularity","daily reference"));return next;
    }
    String report(){StringBuilder b=new StringBuilder("Historical reference quotes · check observation time and granularity\n");b.append(replacements.size()).append(" operations ready; ").append(skipped.size()).append(" skipped.\nOriginal amounts, postings and execution rates remain unchanged. Previous valuations are retained in revisions.\n");for(JSONObject p:replacements)b.append(p.optString("time")).append(" · ").append(p.optString("currency")).append(" · ").append(p.optJSONObject("rate_before_repair").optString("rate")).append(" → ").append(p.optString("rate")).append(" · ").append(p.optString("rate_time")).append(" · ").append(p.optString("rate_granularity")).append('\n');for(String s:skipped)b.append("Skipped: ").append(s).append('\n');return b.toString();}
    void commit(Store store)throws Exception {
        Map<String,JSONObject> current=new HashMap<>();for(JSONObject p:store.entities(false))current.put(p.optString("id"),p);List<JSONObject> events=new ArrayList<>();for(JSONObject p:replacements){JSONObject now=current.get(p.optString("id"));if(now==null||!now.optString("_head").equals(p.optString("_head")))throw new IllegalStateException("An operation changed after preview. Build the preview again.");JSONObject copy=new JSONObject(p.toString());String head=copy.optString("_head");copy.remove("_head");events.add(Ledger.event(copy,head,UUID.randomUUID().toString()));}store.merge(events,true);store.set("rate_repair_report",report());
    }
}
