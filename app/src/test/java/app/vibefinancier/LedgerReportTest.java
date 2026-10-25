package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LedgerReportTest {
    private final List<JSONObject> events=new ArrayList<>();
    private void add(JSONObject p){events.add(Ledger.event(p,"","event."+events.size()));}
    private void account(String id,String currency,boolean debt){add(Ledger.obj("id",id,"entity","account","name",id,"currency",currency,"account_type",debt?"debt":"cash"));}
    private JSONObject transaction(String id,String kind,String account,String amount){JSONObject p=Ledger.transaction(kind,account,amount,"RUB","child","","2026-01-02T00:00:00","RUB","1");try{p.put("id",id);}catch(Exception e){throw new AssertionError(e);}return p;}
    private JSONArray report(String account,String category)throws Exception{return LedgerReport.calculate(events,"RUB","2026-01-01","2026-01-31",account,category);}
    private String value(JSONArray rows,String section,String id){for(int i=0;i<rows.length();i++){JSONArray r=rows.optJSONArray(i);if(r.optString(0).equals(section)&&r.optString(1).equals(id))return r.optString(4);}return null;}
    @Test public void largeValuesAndCryptoRemainExactAfterReplay()throws Exception {
        JSONArray rows=SheetSetup.probeRows();for(int i=0;i<rows.length();i++){JSONArray r=rows.getJSONArray(i);events.add(Ledger.obj("event_id",r.getString(0),"entity_id",r.getString(1),"parents",r.getString(2),"kind",r.getString(3),"payload",r.getString(4),"checksum",r.getString(5)));}
        assertTrue(SheetSetup.probeMatches(report("","")));events.addAll(new ArrayList<>(events));assertTrue(SheetSetup.probeMatches(report("","")));
    }
    @Test public void transfersDebtsAndOpeningsOnlyAffectBalancesAndFees()throws Exception {
        account("cash","RUB",false);account("bank","RUB",false);account("debt","RUB",true);
        add(Ledger.obj("id","opening","entity","transaction","kind","opening","time","2026-01-01T00:00:00","postings",new JSONArray().put(Ledger.obj("account","cash","currency","RUB","amount","100"))));
        JSONObject transfer=transaction("transfer","transfer","cash","20");transfer.put("fee_base","1");transfer.put("postings",new JSONArray().put(Ledger.obj("account","cash","currency","RUB","amount","-21")).put(Ledger.obj("account","bank","currency","RUB","amount","20")));add(transfer);
        JSONObject debt=transaction("loan","debt","bank","5");debt.put("postings",new JSONArray().put(Ledger.obj("account","bank","currency","RUB","amount","-5")).put(Ledger.obj("account","debt","currency","RUB","amount","5")));add(debt);
        JSONArray report=report("","");assertEquals("0",value(report,"income",""));assertEquals("1",value(report,"expense",""));assertEquals("94",value(report,"assets",""));
        // Cash assets are 94; the additional 5 is owed to the user and excluded.
        assertEquals("79",value(report,"balance","cash"));assertEquals("15",value(report,"balance","bank"));assertEquals("5",value(report,"balance","debt"));
        assertEquals("0",value(report("bank",""),"expense",""));
    }
    @Test public void categoryRollupsAndSavedRatesDoNotUseCurrentQuote()throws Exception {
        account("cash","RUB",false);add(Ledger.obj("id","parent","entity","category","name","Parent"));add(Ledger.obj("id","child","entity","category","parent","parent","name","Child"));
        add(transaction("expense","expense","cash","12.34"));add(Ledger.obj("id","quote","entity","rate","currency","USD","base","RUB","rate","200"));
        JSONArray report=report("","parent");assertEquals("12.34",value(report,"expense",""));assertEquals("12.34",value(report,"category_expense","parent"));assertEquals("-12.34",value(report,"month_net","2026-01"));
        assertEquals("0",value(report("","absent"),"expense",""));
    }
    @Test public void missingBaseRatesAndConflictsAreExplicit()throws Exception {
        account("cash","RUB",false);JSONObject tx=transaction("income","income","cash","10");tx.put("base_currency","USD");add(tx);
        JSONArray report=report("","");assertEquals("0",value(report,"income",""));assertEquals("1",value(report,"warning",""));
        events.add(Ledger.event(Ledger.obj("id","cash","entity","account","currency","RUB"),"","concurrent"));report=report("","");assertNotNull(value(report,"conflict","cash"));
    }
}
