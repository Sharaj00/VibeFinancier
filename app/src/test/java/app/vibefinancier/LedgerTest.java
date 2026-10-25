package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LedgerTest {
    private JSONObject account(String id,String currency){return Ledger.obj("id",id,"entity","account","currency",currency);}
    @Test public void exactAmountsAndCurrencyPrecision(){
        assertEquals("9007199254740993.00000001",Money.plain(Money.decimal("9007199254740993.000000001").add(Money.decimal("0.000000009"))));
        assertEquals("0.000000001",Money.input("0.000000001","TON"));assertEquals("0.000001",Money.input("0.000001","USDT"));
        assertThrows(IllegalArgumentException.class,()->Money.input("0.001","RUB"));assertThrows(IllegalArgumentException.class,()->Money.input("1.1","JPY"));
        assertEquals("1.00",Money.convert("1.005","1",2));assertEquals("1.02",Money.convert("1.015","1",2));
    }
    @Test public void duplicateReplayIsIdempotent()throws Exception{
        JSONObject a=Ledger.event(account("cash","RUB"),"","a");JSONObject t=Ledger.event(Ledger.transaction("income","cash","10.25","RUB","","","2026-01-01T00:00:00","RUB","1"),"","t");
        assertEquals("10.25",Ledger.balances(Ledger.entities(List.of(a,t,t),false)).get("cash").toPlainString());
    }
    @Test public void deletionAndRestoration()throws Exception{
        JSONObject a=Ledger.event(account("cash","RUB"),"","a");JSONObject p=Ledger.transaction("income","cash","10","RUB","","","2026-01-01T00:00:00","RUB","1");JSONObject t=Ledger.event(p,"","t");
        p.put("deleted",true);JSONObject d=Ledger.event(p,"t","d");assertEquals("0",Ledger.balances(Ledger.entities(List.of(a,t,d),false)).get("cash").toPlainString());
        p.put("deleted",false);JSONObject r=Ledger.event(p,"d","r");assertEquals("10",Ledger.balances(Ledger.entities(List.of(a,t,d,r),false)).get("cash").toPlainString());
    }
    @Test public void conflictingEditsRequireResolution()throws Exception{
        JSONObject p=account("cash","RUB"),a=Ledger.event(p,"","a"),b=Ledger.event(p,"a","b"),c=Ledger.event(p,"a","c");
        assertEquals(2,Ledger.heads(List.of(a,b,c)).get("cash").size());assertTrue(Ledger.entities(List.of(a,b,c),false).isEmpty());
        assertEquals(1,Ledger.entities(List.of(a,b,c,Ledger.event(p,"b,c","d")),false).size());
    }
    @Test public void invalidHistoryIsRejected()throws Exception{
        JSONObject p=account("cash","RUB"),a=Ledger.event(p,"","a");a.put("payload","{}");assertThrows(JSONException.class,()->Ledger.heads(List.of(a)));
        assertThrows(JSONException.class,()->Ledger.heads(List.of(Ledger.event(p,"missing","a"))));
        assertThrows(JSONException.class,()->Ledger.heads(List.of(Ledger.event(p,"a","a"))));
        assertThrows(JSONException.class,()->Ledger.heads(List.of(Ledger.event(p,"b","a"),Ledger.event(p,"a","b"))));
    }
    @Test public void loansHaveOppositeCashAndDebtLegs()throws Exception{
        JSONObject cash=account("cash","RUB"),debt=account("debt","RUB");JSONObject loan=Ledger.obj("id","loan","entity","transaction","kind","debt","postings",new JSONArray().put(Ledger.obj("account","cash","amount","-100","currency","RUB")).put(Ledger.obj("account","debt","amount","100","currency","RUB")));
        JSONObject repayment=Ledger.obj("id","repay","entity","transaction","kind","debt","postings",new JSONArray().put(Ledger.obj("account","cash","amount","30","currency","RUB")).put(Ledger.obj("account","debt","amount","-30","currency","RUB")));
        var balance=Ledger.balances(List.of(cash,debt,loan,repayment));assertEquals("-70",balance.get("cash").toPlainString());assertEquals("70",balance.get("debt").toPlainString());
    }
    @Test public void transfersKeepBothCurrencyAmounts()throws Exception{
        JSONObject a=account("rub","RUB"),b=account("ton","TON");JSONObject p=Ledger.obj("id","fx","entity","transaction","kind","transfer","postings",new JSONArray().put(Ledger.obj("account","rub","amount","-501.25","currency","RUB")).put(Ledger.obj("account","ton","amount","1.000000001","currency","TON")));
        assertEquals("-501.25",Ledger.balances(List.of(a,b,p)).get("rub").toPlainString());assertEquals("1.000000001",Ledger.balances(List.of(a,b,p)).get("ton").toPlainString());
    }
}
