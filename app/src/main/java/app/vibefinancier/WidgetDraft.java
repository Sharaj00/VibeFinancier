package app.vibefinancier;

import org.json.*;
import java.time.*;
import java.util.*;

/** Persisted per-widget drafts and idempotent double-tap commits. */
public final class WidgetDraft {
    private WidgetDraft(){}
    private static String key(int id){return "widget_draft."+id;}
    public static JSONObject load(Store store,int id)throws Exception {
        String saved=store.get(key(id),"");if(!saved.isEmpty())return new JSONObject(saved);
        List<JSONObject> accounts=choices(store.metadata(),true,"expense"),categories=store.type("category",false);String account=store.get("last_account",""),category="";boolean active=false;for(JSONObject a:accounts)if(a.optString("id").equals(account))active=true;if(!active)account=accounts.isEmpty()?"":accounts.get(0).optString("id");for(JSONObject c:categories)if(c.optString("direction").equals("expense")){category=c.optString("id");break;}
        JSONObject draft=Ledger.obj("nonce",UUID.randomUUID().toString(),"mode","expense","account",account,"destination",category,"amount","");store.set(key(id),draft.toString());return draft;
    }
    static List<JSONObject> choices(List<JSONObject> entities,boolean source,String mode){
        List<JSONObject> result=new ArrayList<>();for(JSONObject item:entities){
            if(item.optBoolean("archived"))continue;boolean account=item.optString("entity").equals("account"),debt=item.optString("account_type").equals("debt");
            if(source?account&&!debt:account||item.optString("entity").equals("category")&&item.optString("direction").equals(mode.equals("income")?"income":"expense"))result.add(item);
        }return result;
    }
    public static void update(Store store,int id,JSONObject draft)throws Exception {draft.put("nonce",UUID.randomUUID().toString());draft.remove("tap");draft.remove("pending");store.set(key(id),draft.toString());}
    public static JSONObject entity(Store store,String id)throws Exception {for(JSONObject p:store.metadata())if(p.optString("id").equals(id)&&!p.optBoolean("archived"))return p;throw new IllegalArgumentException("Choose an active account and destination.");}
    public static JSONObject prepare(Store store,int id,JSONObject draft)throws Exception {
        if(draft.optBoolean("editing"))throw new IllegalArgumentException("Finish the calculation with = before saving.");
        if(draft.optString("amount").isBlank())throw new IllegalArgumentException("Enter an amount before saving.");
        JSONObject from=entity(store,draft.optString("account")),to=entity(store,draft.optString("destination"));if(!from.optString("entity").equals("account")||from.optString("account_type").equals("debt"))throw new IllegalArgumentException("Choose a source account.");
        String currency=from.getString("currency"),base=store.get("base","RUB"),amount=Money.input(draft.optString("amount"),currency),rate=currency.equals(base)?"1":draft.optString("rate"),source="manual",asof=LocalDateTime.now().withNano(0).toString();
        for(JSONObject quote:store.type("rate",true))if(quote.optString("currency").equals(currency)&&quote.optString("base").equals(base)&&rate.isEmpty()){rate=quote.optString("rate");source=quote.optString("source");asof=quote.optString("asof");}
        if(Money.decimal(amount).signum()<=0)throw new IllegalArgumentException("Enter an amount greater than zero.");
        if(rate.isEmpty())throw new IllegalArgumentException("Set a conversion rate in the app before using this currency.");String mode=draft.optString("mode","expense"),kind=mode;
        if(to.optString("entity").equals("account")){kind=from.optString("account_type").equals("debt")||to.optString("account_type").equals("debt")?"debt":"transfer";if(!to.getString("currency").equals(currency))throw new IllegalArgumentException("Use the app for a transfer between different currencies.");if(to.getString("id").equals(from.getString("id")))throw new IllegalArgumentException("Choose two different accounts.");}
        else if(!to.optString("direction").equals(mode)||from.optString("account_type").equals("debt"))throw new IllegalArgumentException("Choose a matching income or expense category and a cash account.");
        String identity="widget."+Ledger.hash(id+"\n"+draft.getString("nonce"));JSONObject tx=Ledger.transaction(kind,from.getString("id"),amount,currency,to.optString("entity").equals("category")?to.getString("id"):"",draft.optString("note"),LocalDateTime.now().withNano(0).toString(),base,rate);tx.put("id",identity);tx.put("timezone",ZoneId.systemDefault().getId());tx.put("rate_source",currency.equals(base)?"identity":source);tx.put("rate_time",asof);
        if(kind.equals("transfer")||kind.equals("debt")){tx.put("to",to.getString("id"));tx.put("received_amount",amount);tx.put("received_currency",currency);tx.put("fee","0");tx.put("fee_base","0");tx.put("postings",new JSONArray().put(Ledger.obj("account",from.getString("id"),"currency",currency,"amount",Money.plain(Money.decimal(amount).negate()))).put(Ledger.obj("account",to.getString("id"),"currency",currency,"amount",amount)));}
        return Ledger.event(tx,"",identity+".event");
    }
    public static void remove(Store store,int id){store.set(key(id),"");}
    static void commit(Store store,int id)throws Exception {
        JSONObject draft=load(store,id);JSONObject event=draft.optJSONObject("pending");
        if(event==null){event=prepare(store,id,draft);draft.put("pending",event);store.set(key(id),draft.toString());}
        entity(store,draft.optString("account"));entity(store,draft.optString("destination"));
        store.merge(List.of(event),true);
        draft.put("amount","");draft.put("expression","");draft.put("editing",false);draft.remove("input_error");draft.remove("note");update(store,id,draft);
    }
}
