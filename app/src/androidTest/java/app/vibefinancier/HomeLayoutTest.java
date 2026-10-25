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
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class HomeLayoutTest {
    private static List<JSONObject> values(){List<JSONObject> values=new ArrayList<>();values.add(Ledger.obj("id","salary","entity","category","name","Salary","direction","income","parent","","icon","coins"));for(int i=0;i<12;i++)values.add(Ledger.obj("id","a"+i,"entity","account","name","Account "+i,"currency","RUB","account_type","cash","icon","wallet"));values.add(Ledger.obj("id","food","entity","category","name","Food","direction","expense","parent","","icon","food"));values.add(Ledger.obj("id","debt","entity","account","name","Debt","currency","RUB","account_type","debt","icon","debt"));return values;}
    private static Dashboard create(Activity a,AtomicReference<JSONObject> route){return new Dashboard(a,values(),Map.of(),Map.of(),"RUB",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"2026-10-01 — 2026-10-25",new Dashboard.Actions(){public void category(JSONObject p){}public void account(JSONObject p){}public void edit(JSONObject p){}public void add(String group){}public void statistics(){}public void operation(JSONObject p){route.set(p);}});}
    @Test public void homeFitsPortraitAndShortLandscapeWithoutVerticalScrolling()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity a=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);
        try{instrument.runOnMainSync(()->{assertFalse("Home must not contain a vertical ScrollView",vertical(a.findViewById(android.R.id.content)));a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);Dashboard view=create(a,new AtomicReference<>());for(int[] size:new int[][]{{360,640},{360,480},{800,290}}){int w=Ui.dp(a,size[0]),h=Ui.dp(a,size[1]);view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);assertEquals(h,view.getHeight());for(String group:List.of("income","account","expense","debt")){View row=view.findViewWithTag("dashboard.row."+group);assertNotNull(row);assertTrue("Every band must be visible",row.getHeight()>0);android.graphics.Rect bounds=new android.graphics.Rect();row.getDrawingRect(bounds);view.offsetDescendantRectToMyCoords(row,bounds);assertTrue(bounds.bottom<=h);assertTrue(bounds.top>=0);}assertFalse(vertical(view));}});
        }finally{instrument.runOnMainSync(a::finish);}
    }
    @Test public void quickSwipeScrollsButHeldIconCanCreateARoute()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity a=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(a);AtomicReference<JSONObject> route=new AtomicReference<>();Dashboard[] dashboard={null};
        try{instrument.runOnMainSync(()->{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);dashboard[0]=create(a,route);a.setContentView(dashboard[0]);});instrument.waitForIdleSync();HorizontalScrollView scroll=dashboard[0].findViewWithTag("dashboard.row.account");int[] location=new int[2];instrument.runOnMainSync(()->scroll.getLocationOnScreen(location));float y=location[1]+scroll.getHeight()/2f;gesture(location[0]+scroll.getWidth()*.8f,y,location[0]+scroll.getWidth()*.25f,y,0);SystemClock.sleep(250);assertTrue("A quick swipe must scroll the account row",scroll.getScrollX()>0);assertNull("A quick swipe must not prepare a transaction",route.get());instrument.runOnMainSync(()->scroll.scrollTo(0,0));instrument.waitForIdleSync();View income=icon(dashboard[0],"salary"),target=icon(dashboard[0],"a0");int[] from=new int[2],to=new int[2];instrument.runOnMainSync(()->{income.getLocationOnScreen(from);target.getLocationOnScreen(to);});gesture(from[0]+income.getWidth()/2f,from[1]+income.getHeight()/2f,to[0]+target.getWidth()/2f,to[1]+target.getHeight()/2f,ImmediateDrag.HOLD_MS+60);assertNotNull(route.get());assertEquals("income",route.get().optString("kind"));assertEquals("a0",route.get().optString("account"));capture(a,"home-fitted-0.1.9.png");
        }finally{instrument.runOnMainSync(a::finish);}
    }
    private static boolean vertical(View v){if(v instanceof ScrollView)return true;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(vertical(g.getChildAt(i)))return true;return false;}
    private static View icon(View v,String id){if(v.getTag() instanceof JSONObject p&&p.optString("id").equals(id))return ((ViewGroup)v).getChildAt(0);if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=icon(g.getChildAt(i),id);if(found!=null)return found;}return null;}
    private static void gesture(float x,float y,float tx,float ty,long hold){var instrument=InstrumentationRegistry.getInstrumentation();long start=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x+(tx-x)*i/12,y+(ty-y)*i/12,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);assertTrue(instrument.getUiAutomation().injectInputEvent(event,true));event.recycle();SystemClock.sleep(i==0&&hold>0?hold:8);}instrument.waitForIdleSync();}
    private static void capture(Activity a,String name)throws Exception {var instrument=InstrumentationRegistry.getInstrumentation();Bitmap bitmap=instrument.getUiAutomation().takeScreenshot();File folder=new File(a.getExternalFilesDir(null),"previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
