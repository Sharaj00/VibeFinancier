package app.vibefinancier;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NavigationGestureTest {
    @Test public void historySwipesSeparateEditingDeletionAndVerticalMovement()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);int[] edits={0},deletes={0};View[] row={null};
        try{
            instrument.runOnMainSync(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout frame=Ui.column(activity);TextView text=Ui.text(activity,"Synthetic history operation",20,Ui.INK);text.setGravity(Gravity.CENTER);text.setMinimumHeight(Ui.dp(activity,120));text.setOnClickListener(v->{});row[0]=new SwipeRow(activity,text,()->edits[0]++,()->deletes[0]++);frame.addView(row[0],new LinearLayout.LayoutParams(-1,Ui.dp(activity,120)));activity.setContentView(frame);});instrument.waitForIdleSync();int[] at=new int[2];instrument.runOnMainSync(()->row[0].getLocationOnScreen(at));float width=row[0].getWidth(),y=at[1]+row[0].getHeight()/2f;
            swipe(at[0]+width*.7f,y,at[0]+width*.1f,y);assertEquals(1,deletes[0]);swipe(at[0]+width*.2f,y,at[0]+width*.9f,y);assertEquals(1,edits[0]);swipe(at[0]+width*.5f,y+30,at[0]+width*.5f,y-30);assertEquals(1,deletes[0]);assertEquals(1,edits[0]);
        }finally{instrument.runOnMainSync(activity::finish);}
    }
    @Test public void drawerAndReportsMatchTheNewNavigationWithoutAccountTabs()throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var context=instrument.getTargetContext();Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);Dialog[] drawer={null};String[] selected={null};
        try{
            instrument.runOnMainSync(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);drawer[0]=NavigationDrawer.show(activity,"Private Google table","All changes saved",value->selected[0]=value);assertNotNull(find(drawer[0].getWindow().getDecorView(),"Menu Reports"));assertNull(find(drawer[0].getWindow().getDecorView(),"Menu Accounts"));assertNull(find(drawer[0].getWindow().getDecorView(),"Menu Debts"));});capture("navigation-drawer.png");instrument.runOnMainSync(()->{find(drawer[0].getWindow().getDecorView(),"Menu Reports").performClick();assertEquals("Reports",selected[0]);});
            List<JSONObject> entities=List.of(Ledger.obj("id","food","entity","category","name","Food","parent","","icon","food","color",0xffc96f52),Ledger.obj("id","housing","entity","category","name","Housing","parent","","icon","home","color",0xffffd74c),Ledger.transaction("expense","cash","30000","RUB","housing","","2026-08-01T12:00:00","RUB","1"),Ledger.transaction("expense","cash","29059.71","RUB","food","","2026-08-02T12:00:00","RUB","1"));ReportData data=new ReportData(entities,"RUB","2026-08-01","2026-08-31","","");
            instrument.runOnMainSync(()->{LinearLayout report=ReportScreen.create(activity,data,"RUB","2026-08-01","2026-08-31","Month",false,new ReportScreen.Actions(){public void shift(int d){}public void period(){}public void filter(){}public void export(){}public void direction(boolean value){}public void category(String id){selected[0]=id;}});activity.setContentView(Ui.scroll(activity,report));});capture("reports-screen.png");instrument.runOnMainSync(()->find(activity.findViewById(android.R.id.content),"Report category Housing").performClick());assertEquals("housing",selected[0]);
        }finally{instrument.runOnMainSync(activity::finish);}
    }
    private static void swipe(float x,float y,float xx,float yy){var automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();long down=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+(xx-x)*i/12,y+(yy-y)*i/12,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);assertTrue(automation.injectInputEvent(event,true));event.recycle();SystemClock.sleep(12);}InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private static View find(View view,String name){if(name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),name);if(found!=null)return found;}return null;}
    private static void capture(String name)throws Exception {SystemClock.sleep(300);var instrument=InstrumentationRegistry.getInstrumentation();Bitmap image=instrument.getUiAutomation().takeScreenshot();File folder=new File(instrument.getTargetContext().getExternalFilesDir(null),"previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
