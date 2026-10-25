package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryDataTest {
    private JSONObject account(String id,String currency,String kind){return Ledger.obj("id",id,"entity","account","name",id,"currency",currency,"account_type",kind);}
    private JSONObject category(String id,String parent,String direction){return Ledger.obj("id",id,"entity","category","name",id,"parent",parent,"direction",direction);}
    @Test public void categoryHistoryIncludesDescendantsAndKeepsOriginalCurrencies()throws Exception {
        JSONObject root=category("shopping","","expense"),child=category("hardware","shopping","expense"),unrelated=category("food","","expense");child.put("archived",true);JSONObject rub=Ledger.transaction("expense","rub","100","RUB","shopping","","2026-08-01T12:00:00","RUB","1"),usd=Ledger.transaction("expense","usd","2","USD","hardware","","2026-08-01T13:00:00","RUB","80"),other=Ledger.transaction("expense","rub","999","RUB","food","","2026-08-02T12:00:00","RUB","1");TransactionExtras.fee(usd,"0.5");List<JSONObject> values=List.of(root,child,unrelated,account("rub","RUB","cash"),account("usd","USD","cash"),rub,usd,other,Ledger.obj("id","rate","entity","rate","currency","USD","base","RUB","rate","999"));HistoryData data=new HistoryData(values,List.of(rub,usd,other),"","shopping","RUB");assertEquals(2,data.count);assertEquals(1,data.days.size());assertEquals("-300",Money.plain(data.total));assertEquals("-100",Money.plain(data.days.get(0).amounts.get("RUB")));assertEquals("-2.5",Money.plain(data.days.get(0).amounts.get("USD")));assertTrue(data.hasFees);assertEquals(0,data.missing);
    }
    @Test public void categoryAccountFilterAndDeletedRowsDoNotPolluteTotals()throws Exception {
        JSONObject category=category("salary","","income"),a=Ledger.transaction("income","a","100","RUB","salary","","2026-08-02T12:00:00","RUB","1"),b=Ledger.transaction("income","b","200","RUB","salary","","2026-08-01T12:00:00","RUB","1"),deleted=Ledger.transaction("income","a","999","RUB","salary","","2026-08-03T12:00:00","RUB","1");deleted.put("deleted",true);HistoryData data=new HistoryData(List.of(category,account("a","RUB","cash"),account("b","RUB","bank_card")),List.of(a,b,deleted),"a","salary","RUB");assertEquals(1,data.count);assertEquals("100",Money.plain(data.total));assertEquals("2026-08-02",data.days.get(0).date);
    }
    @Test public void missingSavedValuationStaysVisibleAndPartial()throws Exception {
        JSONObject p=Ledger.transaction("expense","crypto","0.000000001","TON","fees","","2026-08-01T12:00:00","RUB","100");p.remove("base_amount");HistoryData data=new HistoryData(List.of(category("fees","","expense")),List.of(p),"","fees","RUB");assertEquals(1,data.count);assertEquals(1,data.missing);assertEquals("0",Money.plain(data.total));assertEquals("-0.000000001",Money.plain(data.days.get(0).amounts.get("TON")));
    }
    @Test public void transfersAndDebtAreGroupedAsPostingMovementsNotIncome()throws Exception {
        JSONObject transfer=Ledger.obj("id","move","entity","transaction","kind","transfer","time","2026-08-01T12:00:00","postings",new JSONArray().put(Ledger.obj("account","a","currency","RUB","amount","-103")).put(Ledger.obj("account","b","currency","RUB","amount","100")));HistoryData data=new HistoryData(List.of(account("a","RUB","cash"),account("b","RUB","bank_card")),List.of(transfer),"","","RUB");assertEquals("-3",Money.plain(data.days.get(0).amounts.get("RUB")));HistoryData account=new HistoryData(List.of(),List.of(transfer),"b","","RUB");assertEquals("100",Money.plain(account.days.get(0).amounts.get("RUB")));assertEquals("0",Money.plain(data.total));
    }
    @Test public void debtSummaryExcludesOpeningAndAdjustmentAndRetainsBalance()throws Exception {
        JSONObject debt=account("debt","RUB","debt"),cash=account("cash","RUB","cash"),opening=Ledger.obj("id","opening","entity","transaction","kind","opening","time","2026-08-01T12:00:00","postings",new JSONArray().put(Ledger.obj("account","debt","currency","RUB","amount","-10"))),give=Ledger.obj("id","give","entity","transaction","kind","debt","time","2026-08-02T12:00:00","postings",new JSONArray().put(Ledger.obj("account","cash","currency","RUB","amount","-30")).put(Ledger.obj("account","debt","currency","RUB","amount","30"))),receive=Ledger.obj("id","receive","entity","transaction","kind","debt","time","2026-08-03T12:00:00","postings",new JSONArray().put(Ledger.obj("account","debt","currency","RUB","amount","-5")).put(Ledger.obj("account","cash","currency","RUB","amount","5")));AccountTimeline timeline=new AccountTimeline("debt",List.of(debt,cash,opening,give,receive));assertEquals("15",Money.plain(timeline.balance));assertEquals("30",Money.plain(timeline.toCounterparty));assertEquals("5",Money.plain(timeline.fromCounterparty));assertEquals(BigDecimal.ZERO,timeline.income);assertEquals(BigDecimal.ZERO,timeline.expense);
    }
    @Test public void expenseIsOnlyADropTargetWhileAccountsIncomeAndDebtCanStart()throws Exception {
        JSONObject income=category("salary","","income"),expense=category("food","","expense"),cash=account("cash","RUB","cash"),debt=account("debt","RUB","debt");for(JSONObject p:List.of(income,cash,debt))assertTrue(DragOperation.canStart(p));assertFalse(DragOperation.canStart(expense));assertNull(DragOperation.resolve(expense,cash));assertEquals("expense",DragOperation.resolve(cash,expense).optString("kind"));assertEquals("transfer",DragOperation.resolve(debt,cash).optString("kind"));assertEquals("income",DragOperation.resolve(income,cash).optString("kind"));cash.put("archived",true);assertFalse(DragOperation.canStart(cash));
    }
    @Test public void categoryIndexFindsNestedHistoryWithoutScanningEveryOperationOnTap()throws Exception {
        JSONObject root=category("a","","expense"),child=category("b","a","expense"),tx=Ledger.transaction("expense","cash","5","RUB","b","","2026-08-01T12:00:00","RUB","1");LedgerIndex index=new LedgerIndex(List.of(account("cash","RUB","cash"),root,child,tx));assertEquals(1,index.categoryOperations.get("a").size());assertEquals(tx,index.categoryOperations.get("b").get(0));
    }
}
