package app.vibefinancier;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class PaymentPersistenceTest {
    private Context context;private static final String DB="isolated-payments.db",OTHER="isolated-payments-other.db";
    @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(DB);context.deleteDatabase(OTHER);}
    @After public void after(){context.deleteDatabase(DB);context.deleteDatabase(OTHER);}
    private JSONObject seed(Store s)throws Exception{s.edit(Ledger.obj("id","cash","entity","account","name","Test cash","currency","RUB","account_type","cash"));s.edit(Ledger.obj("id","rent","entity","payment_plan","name","Rent","account","cash","currency","RUB","payment_kind","expense","amount","100","frequency","Monthly","start","2026-01-31","remind_days",1));return s.type("payment_plan",false).get(0);}
    private JSONObject transaction(){return Ledger.transaction("expense","cash","100","RUB","","Rent","2026-02-28T12:00:00","RUB","1");}
    @Test public void confirmationIsAtomicAndSurvivesRestartAndDeletedOperation()throws Exception {
        JSONObject plan;try(Store s=new Store(context,DB)){plan=seed(s);assertEquals("0",Money.plain(Ledger.balances(s.entities(false)).get("cash")));assertTrue(s.confirmPayment(plan,"2026-02-28",transaction()));assertFalse(s.confirmPayment(plan,"2026-02-28",transaction()));assertEquals(1,s.type("transaction",true).size());assertEquals("-100",Money.plain(Ledger.balances(s.entities(false)).get("cash")));}
        try(Store s=new Store(context,DB)){assertFalse(s.confirmPayment(plan,"2026-02-28",transaction()));JSONObject tx=s.type("transaction",true).get(0);tx.put("deleted",true);s.edit(tx);assertFalse("Deleting an operation must not re-open its plan occurrence",s.confirmPayment(plan,"2026-02-28",transaction()));assertEquals("0",Money.plain(Ledger.balances(s.entities(false)).get("cash")));}
    }
    @Test public void skipMakesNoTransactionAndStalePlanCannotBeConfirmed()throws Exception {
        try(Store s=new Store(context,DB)){JSONObject old=seed(s),edit=new JSONObject(old.toString());edit.put("amount","200");s.edit(edit);assertThrows(JSONException.class,()->s.confirmPayment(old,"2026-02-28",transaction()));assertEquals(0,s.type("transaction",true).size());JSONObject plan=s.type("payment_plan",false).get(0);assertTrue(s.confirmPayment(plan,"2026-02-28",null));assertFalse(s.confirmPayment(plan,"2026-02-28",transaction()));assertEquals(0,s.type("transaction",true).size());assertThrows(JSONException.class,()->s.confirmPayment(plan,"2026-02-27",transaction()));}
    }
    @Test public void independentConfirmationsBecomeOneConflictNeverDoubleExpense()throws Exception {
        try(Store a=new Store(context,DB);Store b=new Store(context,OTHER)){JSONObject plan=seed(a);b.merge(a.events(),false);assertTrue(a.confirmPayment(plan,"2026-02-28",transaction()));assertTrue(b.confirmPayment(plan,"2026-02-28",transaction()));a.merge(b.events(),false);assertEquals(1,a.type("payment_receipt",true).size());assertEquals(0,a.type("transaction",true).size());String tx=Payments.occurrence(plan,LocalDate.of(2026,2,28))+".transaction";List<JSONObject> heads=Ledger.heads(a.events()).get(tx);assertEquals(2,heads.size());JSONObject chosen=new JSONObject(heads.get(0).getString("payload"));String parents=heads.get(0).getString("event_id")+","+heads.get(1).getString("event_id");a.resolve(chosen,parents);assertEquals(1,a.type("transaction",true).size());assertEquals("-100",Money.plain(Ledger.balances(a.entities(false)).get("cash")));}
    }
    @Test public void skipAndConfirmOnDifferentDevicesDoNotBreakSync()throws Exception {
        try(Store a=new Store(context,DB);Store b=new Store(context,OTHER)){JSONObject plan=seed(a);b.merge(a.events(),false);a.confirmPayment(plan,"2026-02-28",null);b.confirmPayment(plan,"2026-02-28",transaction());a.merge(b.events(),false);assertEquals(1,a.type("payment_receipt",true).size());assertEquals(1,a.type("transaction",true).size());assertEquals(0,a.merge(b.events(),false));}
    }
    @Test public void backupContainsPlansAndReceiptsAndOfflineQueue()throws Exception {
        try(Store a=new Store(context,DB);Store b=new Store(context,OTHER)){JSONObject plan=seed(a);a.confirmPayment(plan,"2026-02-28",transaction());b.restore(a.backup());assertEquals(1,b.type("payment_plan",true).size());assertEquals(1,b.type("payment_receipt",true).size());assertEquals(1,b.type("transaction",true).size());assertFalse(b.confirmPayment(b.type("payment_plan",false).get(0),"2026-02-28",transaction()));assertTrue(b.pendingCount()>0);assertEquals(0,b.restore(a.backup()));}
    }
    @Test public void unavailableDestinationRollsBackBothOperationAndReceipt()throws Exception {
        try(Store s=new Store(context,DB)){JSONObject plan=seed(s),invalid=transaction();invalid.getJSONArray("postings").put(Ledger.obj("account","missing","currency","RUB","amount","100"));assertThrows(JSONException.class,()->s.confirmPayment(plan,"2026-02-28",invalid));assertTrue(s.type("transaction",true).isEmpty());assertTrue(s.type("payment_receipt",true).isEmpty());assertTrue(s.confirmPayment(plan,"2026-02-28",transaction()));}
    }
}
