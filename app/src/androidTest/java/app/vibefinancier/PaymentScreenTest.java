package app.vibefinancier;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PaymentScreenTest {
    @Test public void calendarAndConfirmationFormUseOnlyIsolatedDatabase()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();String db="isolated-payment-screen.db";context.deleteDatabase(db);
        Activity a=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);AlertDialog[] dialog={null};AtomicBoolean saved=new AtomicBoolean();
        try(Store store=new Store(context,db)){
            store.edit(Ledger.obj("id","cash","entity","account","name","Test cash","currency","RUB","account_type","cash","icon","wallet"));store.edit(Ledger.obj("id","internet","entity","category","name","Internet","direction","expense","icon","phone"));store.edit(Ledger.obj("id","rent","entity","payment_plan","name","Rent","account","cash","category","internet","currency","RUB","payment_kind","expense","amount","100","frequency","Monthly","start",LocalDate.now().toString(),"remind_days",1));JSONObject plan=store.type("payment_plan",false).get(0);List<JSONObject> entities=store.entities(false);
            instrument.runOnMainSync(()->{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout box=Ui.column(a);PaymentScreen.show(a,box,store,entities,()->{});a.setContentView(Ui.scroll(a,box));assertNotNull(box.findViewWithTag("payment.confirm"));});instrument.waitForIdleSync();capture(a,"payments-calendar-1.0.0.png");
            instrument.runOnMainSync(()->{dialog[0]=EntryForm.planned(a,store,false,plan,LocalDate.now().toString(),()->saved.set(true));assertNotNull(dialog[0]);EditText amount=dialog[0].getWindow().getDecorView().findViewWithTag("entry.amount");assertEquals("100",Ui.value(amount));assertEquals(3,store.pendingCount());});
            instrument.waitForIdleSync();capture(a,"payments-confirmation-1.0.0.png");instrument.runOnMainSync(()->dialog[0].getButton(-1).performClick());assertTrue(saved.get());assertEquals(1,store.type("transaction",false).size());assertEquals("100",store.type("transaction",false).get(0).optString("amount"));assertEquals(1,store.type("payment_receipt",false).size());JSONObject recorded=store.type("transaction",false).get(0);instrument.runOnMainSync(()->{dialog[0]=EntryForm.build(a,store,false,"expense",recorded,"","","",()->{},()->{});dialog[0].getButton(-1).performClick();});assertEquals("rent",store.type("transaction",false).get(0).optString("payment_plan"));assertEquals(LocalDate.now().toString(),store.type("transaction",false).get(0).optString("payment_date"));
        }finally{instrument.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();a.finish();});context.deleteDatabase(db);}
    }
    @Test public void drawerHasPaymentsAndNoRemovedActions(){var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity a=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);Dialog[] drawer={null};try{instrument.runOnMainSync(()->{drawer[0]=NavigationDrawer.show(a,"Local storage","Saved",destination->{});View root=drawer[0].getWindow().getDecorView();assertNotNull(find(root,"Menu Payments & subscriptions"));assertNull(find(root,"Menu Home"));assertNull(find(root,"Menu Transfer / debt payment"));});}finally{instrument.runOnMainSync(()->{if(drawer[0]!=null)drawer[0].dismiss();a.finish();});}}
    private static View find(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View match=find(g.getChildAt(i),description);if(match!=null)return match;}return null;}
    private static void capture(Activity a,String name)throws Exception{android.os.SystemClock.sleep(350);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File directory=new File(a.getExternalFilesDir(null),"previews");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
