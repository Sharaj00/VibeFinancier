package app.vibefinancier;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HistoryScreenTest {
    private static final String DB="history-screen-fixture.db";
    private static JSONObject account(String id,String name,String kind){return Ledger.obj("id",id,"entity","account","name",name,"currency","RUB","account_type",kind,"icon",kind.equals("debt")?"percent":"wallet","color",0xffffc84d);}
    private static JSONObject category(String id,String name,String parent,String direction){return Ledger.obj("id",id,"entity","category","name",name,"parent",parent,"direction",direction,"icon","cart","color",0xffffe381);}
    private static void set(Object target,String name,Object value)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object get(Object target,String name)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void render(MainActivity a)throws Exception {Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);}
    private static View tile(View view,String id){if(view.getTag() instanceof JSONObject p&&p.optString("id").equals(id))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=tile(group.getChildAt(i),id);if(found!=null)return found;}return null;}
    private static View text(View view,String value){if(view instanceof TextView t&&t.getText().toString().equals(value))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    @Test public void homeTapsOpenRelatedDailyHistoryWithoutCreatingOperations()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();context.deleteDatabase(DB);MainActivity a=(MainActivity)instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);Store fixture=new Store(context,DB);
        try{
            for(JSONObject p:List.of(account("bank","Bank card","bank_card"),account("debt","Credit","debt"),category("shopping","Shopping","","expense"),category("hardware","Computer parts","shopping","expense"),category("food","Food","","expense"),category("salary","Salary","","income")))fixture.edit(p);
            JSONObject expense=Ledger.transaction("expense","bank","11000","RUB","hardware","Desktop upgrade","2026-08-29T12:00:00","RUB","1");expense.put("id","expense.fixture");fixture.edit(expense);JSONObject unrelated=Ledger.transaction("expense","bank","25","RUB","food","Unrelated category","2026-08-29T13:00:00","RUB","1");unrelated.put("id","unrelated.fixture");fixture.edit(unrelated);
            JSONObject loan=Ledger.transaction("transfer","bank","500","RUB","","Synthetic loan","2026-08-17T12:00:00","RUB","1");loan.put("id","loan.fixture");loan.put("kind","debt");loan.put("to","debt");loan.put("postings",new JSONArray().put(Ledger.obj("account","bank","currency","RUB","amount","-500")).put(Ledger.obj("account","debt","currency","RUB","amount","500")));fixture.edit(loan);
            List<JSONObject> values=fixture.entities(false);LedgerIndex index=new LedgerIndex(values);int transactions=fixture.type("transaction",true).size();instrument.runOnMainSync(()->{try{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Store old=(Store)get(a,"store");set(a,"store",fixture);old.close();set(a,"snapshot",values);set(a,"snapshotToken",fixture.resolvedToken());set(a,"ledgerIndex",index);set(a,"cachedHome",null);set(a,"screen","Home");render(a);View home=a.findViewById(android.R.id.content);View expenseTile=tile(home,"shopping");assertNotNull(expenseTile);assertFalse(expenseTile.isLongClickable());expenseTile.performClick();assertEquals("Category",get(a,"screen"));assertEquals("shopping",get(a,"detailCategory"));View root=a.findViewById(android.R.id.content);assertNotNull(root.findViewWithTag("history.operation.expense.fixture"));assertNull(root.findViewWithTag("history.operation.unrelated.fixture"));assertNotNull(text(root,"Computer parts (Bank card)"));assertNotNull(text(root,"-11 000 ₽"));}catch(Exception e){throw new AssertionError(e);}});capture("category-history-0.1.8.png");
            instrument.runOnMainSync(()->{try{a.onBackPressed();tile(a.findViewById(android.R.id.content),"bank").performClick();assertEquals("Account",get(a,"screen"));assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("history.operation.expense.fixture"));assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("history.operation.unrelated.fixture"));}catch(Exception e){throw new AssertionError(e);}});capture("account-history-0.1.8.png");
            instrument.runOnMainSync(()->{try{a.onBackPressed();tile(a.findViewById(android.R.id.content),"debt").performClick();assertEquals("Account",get(a,"screen"));assertEquals("debt",get(a,"detailAccount"));assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("history.operation.loan.fixture"));assertNull(a.findViewById(android.R.id.content).findViewWithTag("history.operation.expense.fixture"));}catch(Exception e){throw new AssertionError(e);}});capture("debt-history-0.1.8.png");assertEquals(transactions,fixture.type("transaction",true).size());
        }finally{instrument.runOnMainSync(a::finish);fixture.close();context.deleteDatabase(DB);}
    }
    @Test public void debtCanStartNativeDragAndExpenseRemainsOnlyADropTarget()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity a=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);AtomicReference<JSONObject> route=new AtomicReference<>();View[] dashboard={null};
        try{List<JSONObject> values=List.of(account("bank","Bank","cash"),account("debt","Credit","debt"),category("expense","Shopping","","expense"));instrument.runOnMainSync(()->{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Dashboard view=new Dashboard(a,values,Map.of(),Map.of(),"RUB",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"Gesture fixture",new Dashboard.Actions(){public void account(JSONObject p){}public void category(JSONObject p){}public void edit(JSONObject p){}public void add(String group){}public void statistics(){}public void operation(JSONObject p){route.set(p);}});a.setContentView(Ui.scroll(a,view));dashboard[0]=view;});instrument.waitForIdleSync();View source=tile(dashboard[0],"debt"),target=tile(dashboard[0],"bank");drag(source,target);assertNotNull("Debt drag must reach the account",route.get());assertEquals("debt",route.get().optString("account"));assertEquals("bank",route.get().optString("to"));route.set(null);drag(tile(dashboard[0],"expense"),target);assertNull("An expense must never initiate a drag",route.get());
        }finally{instrument.runOnMainSync(a::finish);}
    }
    private static void drag(View from,View to){var instrument=InstrumentationRegistry.getInstrumentation();int[] x=new int[2],y=new int[2];instrument.runOnMainSync(()->{from.getLocationOnScreen(x);to.getLocationOnScreen(y);});float xx=x[0]+from.getWidth()/2f,yy=x[1]+from.getHeight()/2f,tx=y[0]+to.getWidth()/2f,ty=y[1]+to.getHeight()/2f;long down=SystemClock.uptimeMillis();for(int i=0;i<=15;i++){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==15?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,xx+(tx-xx)*i/15,yy+(ty-yy)*i/15,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);assertTrue(instrument.getUiAutomation().injectInputEvent(e,true));e.recycle();SystemClock.sleep(i==0?ImmediateDrag.HOLD_MS+60:20);}instrument.waitForIdleSync();}
    private static void capture(String name)throws Exception {SystemClock.sleep(250);var instrument=InstrumentationRegistry.getInstrumentation();Bitmap image=instrument.getUiAutomation().takeScreenshot();File folder=new File(instrument.getTargetContext().getExternalFilesDir(null),"previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
