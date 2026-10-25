package app.vibefinancier;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetInputTest {

    private void seed(Store store)throws Exception {
        store.edit(Ledger.obj("id","bank","entity","account","name","Bank","currency","RUB","account_type","cash"));
        store.edit(Ledger.obj("id","food","entity","category","name","Food","direction","expense","parent","","icon","food"));
        store.edit(Ledger.obj("id","lunch","entity","category","name","Lunch","direction","expense","parent","food","icon","food"));
    }
    @Test public void expressionSurvivesRestartAndOnlyConfirmationRecordsMoney()throws Exception {
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-widget-expression.db";c.deleteDatabase(db);
        try(Store store=new Store(c,db)){
            seed(store);WidgetInput.expression(store,81,"30+50*2/4");assertThrows(Exception.class,()->WidgetDraft.commit(store,81));assertEquals(0,store.type("transaction",true).size());
        }
        try(Store store=new Store(c,db)){
            assertEquals("30+50*2/4",WidgetDraft.load(store,81).getString("expression"));WidgetInput.finish(store,81);assertEquals("55",WidgetDraft.load(store,81).getString("amount"));assertEquals(0,store.type("transaction",true).size());
            WidgetDraft.commit(store,81);assertEquals(1,store.type("transaction",true).size());assertEquals("55",store.type("transaction",true).get(0).getString("amount"));assertEquals("",WidgetDraft.load(store,81).getString("amount"));assertFalse(WidgetDraft.load(store,81).getBoolean("editing"));assertThrows(Exception.class,()->WidgetDraft.commit(store,81));assertEquals(1,store.type("transaction",true).size());
        }finally{c.deleteDatabase(db);}
    }
    @Test public void invalidExpressionDoesNotOverwriteLastValidAmount()throws Exception {
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-widget-expression-error.db";c.deleteDatabase(db);
        try(Store store=new Store(c,db)){
            seed(store);WidgetInput.expression(store,82,"0.1+0.2");WidgetInput.finish(store,82);assertEquals("0.3",WidgetDraft.load(store,82).getString("amount"));
            WidgetInput.expression(store,82,"1/0");assertThrows(Exception.class,()->WidgetInput.finish(store,82));assertEquals("0.3",WidgetDraft.load(store,82).getString("amount"));assertThrows(Exception.class,()->WidgetDraft.commit(store,82));
            WidgetInput.cancel(store,82);assertFalse(WidgetDraft.load(store,82).getBoolean("editing"));assertEquals("0.3",WidgetDraft.load(store,82).getString("amount"));
            WidgetInput.expression(store,82,"1.0050000000000000000000000000000000000001");WidgetInput.finish(store,82);assertEquals("1.01",WidgetDraft.load(store,82).getString("amount"));
        }finally{c.deleteDatabase(db);}
    }
    @Test public void compactPopupNavigatesParentThenSubcategoryAndShowsCheck()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var c=instrument.getTargetContext();String db="isolated-widget-hierarchy.db";c.deleteDatabase(db);
        Activity activity=instrument.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);instrument.runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        assertFalse("Unlock the phone for visual widget checks",((KeyguardManager)c.getSystemService(android.content.Context.KEYGUARD_SERVICE)).isKeyguardLocked());
        try(Store store=new Store(c,db)){
            seed(store);AlertDialog[] dialog={null};JSONObject[] picked={null};
            instrument.runOnMainSync(()->{try{dialog[0]=WidgetPopup.chooser(activity,store,false,WidgetDraft.load(store,83),item->picked[0]=item,()->{});assertNull(find(dialog[0].getWindow().getDecorView(),"Lunch"));find(dialog[0].getWindow().getDecorView(),"Food").performClick();assertNull(picked[0]);assertNotNull(find(dialog[0].getWindow().getDecorView(),"Lunch"));}catch(Exception e){throw new AssertionError(e);}});
            capture("widget-subcategory.png");instrument.runOnMainSync(()->find(dialog[0].getWindow().getDecorView(),"Lunch").performClick());assertEquals("lunch",picked[0].getString("id"));assertEquals(0,store.type("transaction",true).size());
            instrument.runOnMainSync(()->WidgetPopup.saved(activity,()->{}));capture("widget-saved.png");
        }finally{instrument.runOnMainSync(activity::finish);c.deleteDatabase(db);}
    }
    private static View find(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}return null;}
    private static void capture(String name)throws Exception {android.os.SystemClock.sleep(350);var instrument=InstrumentationRegistry.getInstrumentation();Bitmap image=instrument.getUiAutomation().takeScreenshot();File dir=new File(instrument.getTargetContext().getExternalFilesDir(null),"previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
