package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

public class InteractionTest {
    private JSONObject account(String id,boolean debt){return Ledger.obj("id",id,"entity","account","currency","RUB","account_type",debt?"debt":"cash");}
    private JSONObject category(String id,String parent,String direction){return Ledger.obj("id",id,"entity","category","name",id,"parent",parent,"direction",direction);}
    @Test public void calculatorUsesExactArithmeticAndFinalCurrencyRounding(){
        assertEquals("0.3",Calculator.evaluate("0.1+0.2",2));assertEquals("9007199254740993.01",Calculator.evaluate("9007199254740993+0.01",2));
        assertEquals("1",Calculator.evaluate("1/3*3",2));assertEquals("14",Calculator.evaluate("2+3*4",2));assertEquals("20",Calculator.evaluate("(2+3)*4",2));
        assertEquals("1",Calculator.evaluate("1.005",2));assertEquals("1.02",Calculator.evaluate("1.015",2));assertEquals("10",Calculator.evaluate("200*5%",2));
        assertEquals("0.000000001",Calculator.evaluate("0.000000003/3",9));assertEquals("-3.5",Calculator.evaluate("-2,5-1",2));
        assertEquals("1.01",Calculator.evaluate("1.0050000000000000000000000000000000000001/1",2));
        assertThrows(IllegalArgumentException.class,()->Calculator.evaluate("1/0",2));assertThrows(IllegalArgumentException.class,()->Calculator.evaluate("1+",2));assertThrows(IllegalArgumentException.class,()->Calculator.evaluate("alert(1)",2));
    }
    @Test public void dragResolvesDirectionWithoutRecordingAnOperation(){
        JSONObject cash=account("cash",false),bank=account("bank",false),debt=account("debt",true),income=category("income","","income"),expense=category("expense","","expense");
        assertEquals("income",DragOperation.resolve(income,bank).optString("kind"));assertEquals("bank",DragOperation.resolve(income,bank).optString("account"));
        assertEquals("expense",DragOperation.resolve(bank,expense).optString("kind"));assertNull(DragOperation.resolve(expense,bank));
        assertEquals("bank",DragOperation.resolve(cash,bank).optString("to"));assertEquals("debt",DragOperation.resolve(cash,debt).optString("to"));
        assertNull(DragOperation.resolve(cash,cash));assertNull(DragOperation.resolve(income,expense));assertNull(DragOperation.resolve(income,debt));
    }
    @Test public void nestedCategoryChoicesKeepTheSelectedDescendant(){
        List<JSONObject> categories=List.of(category("food","","expense"),category("shop","food","expense"),category("delivery","shop","expense"));
        assertEquals("food",CategoryChoices.root("delivery",categories));assertEquals("food › shop › delivery",CategoryChoices.path(categories.get(2),categories));
        assertThrows(IllegalArgumentException.class,()->CategoryChoices.root("a",List.of(category("a","b","expense"),category("b","a","expense"))));
    }
    @Test public void balanceEditingCreatesAnAdjustmentWithoutTouchingHistory()throws Exception {
        JSONObject account=account("cash",false);account.put("_head","original");List<JSONObject> edit=AccountEdits.events(account,new BigDecimal("-33000"),"-32000",false,"2026-10-23T12:00:00");
        assertEquals(2,edit.size());JSONObject adjustment=new JSONObject(edit.get(1).getString("payload"));assertEquals("adjustment",adjustment.getString("kind"));assertEquals("1000",adjustment.getString("amount"));assertEquals("original",edit.get(0).getString("parents"));
        assertEquals(1,AccountEdits.events(account,new BigDecimal("1.571"),"1.571",false,"2026-01-01T00:00:00").size());
        assertThrows(IllegalArgumentException.class,()->AccountEdits.events(account,BigDecimal.ZERO,"1.001",false,"2026-01-01T00:00:00"));
    }
    @Test public void accountDaysAndExcludedAssetsKeepTheFullLedgerBalance()throws Exception {
        JSONObject cash=account("cash",false);cash.put("exclude_from_total",true);JSONObject income=Ledger.transaction("income","cash","100","RUB","","","2026-01-01T12:00:00","RUB","1"),expense=Ledger.transaction("expense","cash","30","RUB","","","2026-01-02T12:00:00","RUB","1");
        AccountTimeline timeline=new AccountTimeline("cash",List.of(cash,income,expense));assertEquals("70",Money.plain(timeline.balance));assertEquals("70",Money.plain(timeline.days.get(0).closing));assertEquals("100",Money.plain(timeline.days.get(1).closing));assertEquals("100",Money.plain(timeline.income));assertEquals("30",Money.plain(timeline.expense));
        JSONArray rows=LedgerReport.calculate(List.of(Ledger.event(cash,"","a"),Ledger.event(income,"","i"),Ledger.event(expense,"","e")),"RUB","2026-01-01","2026-01-31","","");for(int i=0;i<rows.length();i++){JSONArray r=rows.getJSONArray(i);if(r.optString(0).equals("assets"))assertEquals("0",r.getString(4));if(r.optString(0).equals("balance"))assertEquals("70",r.getString(4));}
    }
}
