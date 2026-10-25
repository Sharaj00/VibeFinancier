package app.vibefinancier;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;

final class TestUi {
    static void ready(Activity activity){var instrument=InstrumentationRegistry.getInstrumentation();boolean[] ready={false};long deadline=SystemClock.uptimeMillis()+15000;while(!ready[0]&&SystemClock.uptimeMillis()<deadline){instrument.runOnMainSync(()->ready[0]=activity.findViewById(android.R.id.content).findViewWithTag("ledger.ready")!=null);if(!ready[0])SystemClock.sleep(30);}assertTrue("Initial ledger load must complete before replacing the test UI",ready[0]);}
}
