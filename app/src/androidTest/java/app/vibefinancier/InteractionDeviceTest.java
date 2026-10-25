package app.vibefinancier;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InteractionDeviceTest {
    private static final String DB="isolated-interaction-test.db";
    private JSONObject account(String id,String name){return Ledger.obj("id",id,"entity","account","name",name,"currency","RUB","account_type","cash");}
    private JSONObject category(String id,String name,String direction,String parent){return Ledger.obj("id",id,"entity","category","name",name,"direction",direction,"parent",parent);}
    private void seed(Store store)throws Exception{for(JSONObject p:List.of(account("cash","Cash"),account("bank","Bank card"),category("income","Salary","income",""),category("food","Food","expense",""),category("lunch","Lunch","expense","food")))store.edit(p);}
    @Test public void nativeDragOpensPrefilledIncomeWithoutSaving()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();context.deleteDatabase(DB);Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);
        instrument.runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));assertFalse("Unlock the phone for gesture and screenshot checks",((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
        try(Store store=new Store(context,DB)){
            seed(store);AtomicReference<JSONObject> route=new AtomicReference<>();AtomicReference<AlertDialog> dialog=new AtomicReference<>();View[] dashboard={null};
            instrument.runOnMainSync(()->{try{Dashboard view=new Dashboard(activity,store.entities(false),Map.of(),Map.of(),"RUB",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"Synthetic gesture check",new Dashboard.Actions(){public void category(JSONObject p){}public void account(JSONObject p){}public void edit(JSONObject p){}public void add(String group){}public void statistics(){}public void operation(JSONObject p){route.set(p);dialog.set(EntryForm.build(activity,store,false,p.optString("kind"),null,p.optString("account"),p.optString("category"),p.optString("to"),()->{},()->{}));}});ScrollView scroll=new ScrollView(activity);scroll.addView(view);activity.setContentView(scroll);dashboard[0]=view;}catch(Exception e){throw new AssertionError(e);}});
            instrument.waitForIdleSync();View source=findTile(dashboard[0],"income"),target=findTile(dashboard[0],"bank");assertNotNull(source);assertNotNull(target);int[][] positions=new int[2][2];instrument.runOnMainSync(()->{source.getLocationOnScreen(positions[0]);target.getLocationOnScreen(positions[1]);});
            float x=positions[0][0]+source.getWidth()/2f,y=positions[0][1]+source.getHeight()/2f,xx=positions[1][0]+target.getWidth()/2f,yy=positions[1][1]+target.getHeight()/2f;long down=SystemClock.uptimeMillis();touch(instrument,down,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(ImmediateDrag.HOLD_MS+60);
            for(int i=1;i<=16;i++){touch(instrument,down,MotionEvent.ACTION_MOVE,x+(xx-x)*i/16,y+(yy-y)*i/16);SystemClock.sleep(25);}touch(instrument,down,MotionEvent.ACTION_UP,xx,yy);instrument.waitForIdleSync();
            assertNotNull("Native drag must reach the drop target",route.get());assertEquals("income",route.get().optString("kind"));assertEquals("bank",route.get().optString("account"));assertEquals(0,store.type("transaction",true).size());
            instrument.runOnMainSync(()->{Spinner spinner=dialog.get().getWindow().getDecorView().findViewWithTag("entry.account");assertEquals(1,spinner.getSelectedItemPosition());dialog.get().getButton(-2).performClick();});assertEquals(0,store.type("transaction",true).size());
        }finally{instrument.runOnMainSync(activity::finish);context.deleteDatabase(DB);}
    }
    @Test public void entryCalculatorDateSubcategoryAndCommentSaveOnlyToIsolatedDatabase()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();context.deleteDatabase(DB);Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);
        instrument.runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));assertFalse("Unlock the phone for gesture and screenshot checks",((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
        try(Store store=new Store(context,DB)){
            seed(store);AlertDialog[] dialog={null};instrument.runOnMainSync(()->{LinearLayout background=Ui.column(activity);background.addView(Ui.text(activity,"Synthetic entry check",20,Ui.INK));activity.setContentView(background);dialog[0]=EntryForm.build(activity,store,false,"expense",null,"bank","lunch","",()->{},()->{});});instrument.waitForIdleSync();
            instrument.runOnMainSync(()->{View root=dialog[0].getWindow().getDecorView();assertEquals(1,((Spinner)root.findViewWithTag("entry.subcategory")).getSelectedItemPosition());((EditText)root.findViewWithTag("entry.amount")).setText("100+25*2");findText(root,"=").performClick();assertEquals("150",((EditText)root.findViewWithTag("entry.amount")).getText().toString());root.findViewWithTag("entry.date").performClick();});instrument.waitForIdleSync();
            instrument.runOnMainSync(()->{for(View root:WindowInspector.getGlobalWindowViews()){DatePicker picker=findType(root,DatePicker.class);if(picker!=null){picker.updateDate(2026,9,20);((Button)root.findViewById(android.R.id.button1)).performClick();break;}}dialog[0].getWindow().getDecorView().findViewWithTag("entry.time").performClick();});instrument.waitForIdleSync();
            instrument.runOnMainSync(()->{for(View root:WindowInspector.getGlobalWindowViews()){TimePicker picker=findType(root,TimePicker.class);if(picker!=null){picker.setHour(14);picker.setMinute(35);((Button)root.findViewById(android.R.id.button1)).performClick();break;}}View root=dialog[0].getWindow().getDecorView();((EditText)root.findViewWithTag("entry.note")).setText("Lunch after drag");((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);});instrument.waitForIdleSync();
            SystemClock.sleep(500);Bitmap screenshot=instrument.getUiAutomation().takeScreenshot();File directory=new File(context.getExternalFilesDir(null),"previews");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,"entry-form.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
            instrument.runOnMainSync(()->dialog[0].getButton(-1).performClick());assertEquals(1,store.type("transaction",true).size());JSONObject tx=store.type("transaction",true).get(0);assertEquals("150",tx.getString("amount"));assertEquals("bank",tx.getString("account"));assertEquals("lunch",tx.getString("category"));assertEquals("2026-10-20T14:35",tx.getString("time"));assertEquals("Lunch after drag",tx.getString("note"));
        }finally{instrument.runOnMainSync(activity::finish);context.deleteDatabase(DB);}
    }
    private static void touch(android.app.Instrumentation instrument,long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);assertTrue(instrument.getUiAutomation().injectInputEvent(event,true));event.recycle();}
    @Test public void accountCardEditorAndVisualIconPickerRenderWithoutSaving()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();context.deleteDatabase(DB);Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);
        instrument.runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));assertFalse("Unlock the phone for gesture and screenshot checks",((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
        try(Store store=new Store(context,DB)){
            seed(store);store.edit(Ledger.transaction("income","bank","1000","RUB","income","","2026-10-01T12:00:00","RUB","1"));store.edit(Ledger.transaction("expense","bank","50","RUB","lunch","","2026-10-02T12:00:00","RUB","1"));int count=store.events().size();JSONObject account=store.type("account",false).get(1);AlertDialog[] dialog={null};
            instrument.runOnMainSync(()->{try{LinearLayout box=Ui.column(activity);box.addView(Ui.text(activity,"Account · synthetic preview",20,Ui.INK));box.addView(AccountCard.create(activity,account,new AccountTimeline("bank",store.entities(false)),()->{}));activity.setContentView(box);}catch(Exception e){throw new AssertionError(e);}});instrument.waitForIdleSync();capture("account-card.png");
            instrument.runOnMainSync(()->{try{dialog[0]=AccountForm.show(activity,store,store.entities(false),account,"cash",()->{});}catch(Exception e){throw new AssertionError(e);}});instrument.waitForIdleSync();capture("account-edit.png");
            instrument.runOnMainSync(()->findText(dialog[0].getWindow().getDecorView(),Strings.t(activity,"Choose icon & color")).performClick());instrument.waitForIdleSync();capture("icon-picker.png");
            instrument.runOnMainSync(()->{View root=windowWithButton(Strings.t(activity,"Done"));findDescription(root,"Color #78B85B").performClick();((View)findText(root,"food").getParent()).performClick();IconPicker picker=findType(dialog[0].getWindow().getDecorView(),IconPicker.class);assertEquals("food",picker.icon());assertEquals(0xff78b85b,picker.color());dialog[0].getButton(-2).performClick();});assertEquals(count,store.events().size());
        }finally{instrument.runOnMainSync(activity::finish);context.deleteDatabase(DB);}
    }
    private static void capture(String name)throws Exception {SystemClock.sleep(500);var instrument=InstrumentationRegistry.getInstrumentation();Bitmap image=instrument.getUiAutomation().takeScreenshot();File directory=new File(instrument.getTargetContext().getExternalFilesDir(null),"previews");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private static View findDescription(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View findTile(View v,String id){if(v.getTag() instanceof JSONObject p&&p.optString("id").equals(id))return v;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findTile(group.getChildAt(i),id);if(found!=null)return found;}return null;}
    private static View findText(View v,String text){if(v instanceof TextView t&&t.getText().toString().equals(text))return v;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private static View windowWithButton(String label){for(View root:WindowInspector.getGlobalWindowViews())if(findText(root,label) instanceof Button)return root;return null;}
    private static <T extends View>T findType(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=findType(group.getChildAt(i),type);if(found!=null)return found;}return null;}
}
