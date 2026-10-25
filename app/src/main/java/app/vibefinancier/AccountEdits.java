package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

public final class AccountEdits {
    private AccountEdits(){}
    public static List<JSONObject> events(JSONObject account,BigDecimal previous,String desired,boolean creating,String time)throws Exception {
        BigDecimal target=Money.decimal(desired.replace(',','.'));if(target.compareTo(previous)!=0)Money.input(target.abs().toPlainString(),account.getString("currency"));
        JSONObject payload=new JSONObject(account.toString());String head=payload.optString("_head");payload.remove("_head");List<JSONObject> events=new ArrayList<>();events.add(Ledger.event(payload,head,UUID.randomUUID().toString()));
        BigDecimal delta=target.subtract(previous);
        if(delta.signum()!=0){String amount=Money.plain(delta);JSONObject adjustment=Ledger.obj("id",UUID.randomUUID().toString(),"entity","transaction","kind",creating?"opening":"adjustment","amount",amount,"currency",payload.getString("currency"),"account",payload.getString("id"),"note",creating?"Initial balance":"Balance changed in account settings","time",time,"postings",new JSONArray().put(Ledger.obj("account",payload.getString("id"),"currency",payload.getString("currency"),"amount",amount)));events.add(Ledger.event(adjustment,"",UUID.randomUUID().toString()));}
        return events;
    }
}
