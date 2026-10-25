package app.vibefinancier;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetSvgTest {
    private static final String SVG="<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"#dd3333\" d=\"M2 2h20v20H2z\"/></svg>";
    private void seed(Store store)throws Exception {store.edit(Ledger.obj("id","bank","entity","account","name","Bank card","currency","RUB","account_type","bank_card","icon","card"));store.edit(Ledger.obj("id","food","entity","category","name","Food","direction","expense","parent","","icon","food"));}
    @Test public void destinationChoicesIncludeAccountsExpensesAndDebts()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-widget-choices.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            seed(store);store.edit(Ledger.obj("id","debt","entity","account","name","Debt","currency","RUB","account_type","debt"));store.edit(Ledger.obj("id","salary","entity","category","name","Salary","direction","income"));
            assertEquals(1,WidgetDraft.choices(store.entities(false),true,"expense").size());assertEquals(3,WidgetDraft.choices(store.entities(false),false,"expense").size());
            JSONObject draft=WidgetDraft.load(store,8);draft.put("account","debt");draft.put("destination","food");draft.put("amount","1");assertThrows(Exception.class,()->WidgetDraft.prepare(store,8,draft));
        }finally{context.deleteDatabase(db);}
    }
    @Test public void iconChoicePopupRendersAndSelectsWithoutRecordingMoney()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();String db="isolated-widget-popup.db";context.deleteDatabase(db);
        android.app.Activity activity=instrument.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);
        instrument.runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        try(Store store=new Store(context,db)){
            seed(store);store.edit(Ledger.obj("id","debt","entity","account","name","Debt","currency","RUB","account_type","debt"));org.json.JSONObject[] selected={null};android.app.AlertDialog[] dialog={null};
            instrument.runOnMainSync(()->{try{dialog[0]=WidgetDraftActivity.chooser(activity,store,false,WidgetDraft.load(store,123),item->selected[0]=item,()->{});}catch(Exception e){throw new AssertionError(e);}});
            instrument.waitForIdleSync();android.os.SystemClock.sleep(500);Bitmap image=instrument.getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"widget-choices.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
            instrument.runOnMainSync(()->findDescription(dialog[0].getWindow().getDecorView(),"Debt").performClick());assertEquals("debt",selected[0].optString("id"));assertEquals(0,store.type("transaction",true).size());
        }finally{instrument.runOnMainSync(activity::finish);context.deleteDatabase(db);}
    }
    private static View findDescription(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),label);if(found!=null)return found;}return null;}
    @Test public void commitPersistsOneOfflineOperationAndClearsAmount()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-widget-tap.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            seed(store);JSONObject draft=WidgetDraft.load(store,42);draft.put("amount","12.34");WidgetDraft.update(store,42,draft);
            assertEquals(0,store.type("transaction",true).size());
        }
        try(Store store=new Store(context,db)){
            WidgetDraft.commit(store,42);assertEquals(1,store.type("transaction",true).size());assertEquals("12.34",store.type("transaction",true).get(0).getString("amount"));assertEquals("",WidgetDraft.load(store,42).getString("amount"));
            assertThrows(Exception.class,()->WidgetDraft.commit(store,42));assertEquals(1,store.type("transaction",true).size());assertTrue(store.pendingCount()>0);
        }finally{context.deleteDatabase(db);}
    }
    @Test public void crashAfterCommitReconcilesSameEventWithoutDuplicate()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-widget-crash.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            seed(store);JSONObject draft=WidgetDraft.load(store,7);draft.put("amount","10");WidgetDraft.update(store,7,draft);draft=WidgetDraft.load(store,7);draft.put("pending",WidgetDraft.prepare(store,7,draft));store.set("widget_draft.7",draft.toString());store.merge(java.util.List.of(draft.getJSONObject("pending")),true);
            WidgetDraft.commit(store,7);assertEquals(1,store.type("transaction",true).size());assertEquals("",WidgetDraft.load(store,7).getString("amount"));
        }finally{context.deleteDatabase(db);}
    }
    @Test public void customSvgValidatesAndSurvivesBackupRestore()throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();String db="isolated-svg.db",restoreDb="isolated-svg-restore.db";context.deleteDatabase(db);context.deleteDatabase(restoreDb);
        try(Store store=new Store(context,db);Store restored=new Store(context,restoreDb)){
            assertEquals(SVG,SvgIcons.validate(SVG));
            assertThrows(Exception.class,()->SvgIcons.validate("<!DOCTYPE svg><svg/>"));assertThrows(Exception.class,()->SvgIcons.validate(SVG.replace("<path","<script")));assertThrows(Exception.class,()->SvgIcons.validate("<svg viewBox=\"0 0 24 24\"><use href=\"https://example.com/icon.svg\"/></svg>"));
            JSONObject account=Ledger.obj("id","custom","entity","account","name","My SVG","currency","RUB","icon","custom","icon_svg",SVG);store.edit(account);restored.restore(store.backup());assertEquals(SVG,restored.type("account",false).get(0).getString("icon_svg"));
            Bitmap image=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);assertTrue(SvgIcons.draw(context,canvas,IconCatalog.resolve(account),Color.BLACK));assertEquals(0xffdd3333,image.getPixel(50,50));assertEquals(0xffdd3333,image.getPixel(70,70));assertEquals(0,Color.alpha(image.getPixel(15,15)));image.recycle();
            for(String key:IconCatalog.ICONS){Bitmap icon=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);assertTrue("Missing Lucide icon: "+key,SvgIcons.draw(context,new Canvas(icon),key,Color.BLACK));icon.recycle();}
        }finally{context.deleteDatabase(db);context.deleteDatabase(restoreDb);}
    }
    @Test public void nativeWidgetRendersSourceAmountAndDestination()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();String db="isolated-widget-preview.db";context.deleteDatabase(db);
        try(Store store=new Store(context,db)){
            seed(store);JSONObject draft=WidgetDraft.load(store,99);draft.put("amount","1250");WidgetDraft.update(store,99,draft);
            instrument.runOnMainSync(()->{try{View view=FinanceWidget.views(context,99,store).apply(context,new FrameLayout(context));int width=(int)(350*context.getResources().getDisplayMetrics().density),height=(int)(120*context.getResources().getDisplayMetrics().density);view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);assertEquals("1250 RUB",((TextView)view.findViewById(R.id.widget_amount)).getText().toString());assertEquals("Bank card",((TextView)view.findViewById(R.id.widget_from_label)).getText().toString());assertEquals("Food",((TextView)view.findViewById(R.id.widget_to_label)).getText().toString());Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));assertEquals(0,Color.alpha(image.getPixel(0,0)));assertEquals(View.VISIBLE,view.findViewById(R.id.widget_title).getVisibility());assertEquals("VibeFinancier",((TextView)view.findViewById(R.id.widget_title)).getText().toString());assertNotNull(view.findViewById(R.id.widget_save));File dir=new File(context.getExternalFilesDir(null),"previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"widget.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}catch(Exception e){throw new AssertionError(e);}});
        }finally{context.deleteDatabase(db);}
    }
}
