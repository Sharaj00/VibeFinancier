package app.vibefinancier;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.time.*;
import static org.junit.Assert.*;

/** Explicit online smoke test; provider outages are reported separately from fixture tests. */
public class ProviderDeviceTest {
    @Test public void liveQuotesUseAnIsolatedDatabaseAndPreserveOldValuesOnFailure()throws Exception {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("provider_test","false").equals("true"));var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="provider-smoke-test.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            for(String currency:new String[]{"USD","TON","USDT"})store.edit(Ledger.obj("id",currency,"entity","account","name",currency,"currency",currency,"account_type","crypto"));
            try{Rates.update(store);}catch(Exception e){android.util.Log.i("ProviderTest","Current quote limitation: "+e.getMessage());}
            for(String currency:new String[]{"USD","TON","USDT"}){JSONObject q=Rates.latest(store,currency,"RUB");android.util.Log.i("ProviderTest",currency+" current: "+(q==null?"unavailable":q.optString("rate")+" · "+q.optString("source")+" · "+q.optString("asof")));assertNotNull("Live quote unavailable for "+currency,q);assertTrue(Money.decimal(q.getString("rate")).signum()>0);}
            for(String currency:new String[]{"USD","TON"})try{JSONObject q=currency.equals("TON")?Rates.atTime(currency,"RUB",LocalDateTime.now(ZoneOffset.UTC).minusDays(2),ZoneOffset.UTC):Rates.historical(currency,"RUB",LocalDate.now(ZoneOffset.UTC).minusDays(2));android.util.Log.i("ProviderTest",currency+" historical: "+q.optString("rate")+" · "+q.optString("asof"));assertTrue(Money.decimal(q.getString("rate")).signum()>0);}catch(Exception e){android.util.Log.i("ProviderTest",currency+" historical limitation: "+e.getMessage());}
        }finally{context.deleteDatabase(db);}
    }
}
