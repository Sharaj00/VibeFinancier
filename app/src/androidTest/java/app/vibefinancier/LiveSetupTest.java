package app.vibefinancier;

import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicit opt-in device check; normal test runs never open the user's database. */
@RunWith(AndroidJUnit4.class)
public class LiveSetupTest {
    @Test public void finishAuthorizedEmptyLedgerSetup()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("live_setup")));
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();
        try(Store store=new Store(context)){
            assertTrue("Live setup check requires an empty local ledger",store.events().isEmpty());
            Cloud cloud=new Cloud(Cloud.backgroundToken(context),phase->store.set("google_stage",java.time.Instant.now()+"\n"+phase));
            SheetSetup setup=new SheetSetup(cloud);String id=setup.create(store);assertEquals(id,setup.create(store));
            assertTrue("Only empty remote ledgers may be used by this check",cloud.readEvents(id,cloud.journals(id)).isEmpty());
            cloud.sync(store);assertEquals(0,store.pendingCount());
            Bundle result=new Bundle();result.putString("stream","Private table ready; exact storage and repeat setup passed. No financial history uploaded.\n"+SheetSetup.url(id)+"\n");instrument.sendStatus(0,result);
        }
    }
}
