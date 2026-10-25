package app.vibefinancier;

import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native ViewGroup dispatch checks that do not require touching a locked screen. */
@RunWith(AndroidJUnit4.class)
public class SwipeDispatchTest {
    @Test public void horizontalSwipesChooseTheCorrectActionAndVerticalMotionDoesNothing(){
        var instrument=InstrumentationRegistry.getInstrumentation();int[] edits={0},deletes={0};
        instrument.runOnMainSync(()->{
            var context=instrument.getTargetContext();FrameLayout parent=new FrameLayout(context);TextView content=new TextView(context);content.setText("Synthetic transaction");content.setMinimumHeight(300);content.setOnClickListener(v->{});SwipeRow row=new SwipeRow(context,content,()->edits[0]++,()->deletes[0]++);parent.addView(row,new FrameLayout.LayoutParams(1080,300));parent.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));parent.layout(0,0,1080,300);
            drag(row,850,150,100,150);assertEquals(1,deletes[0]);assertEquals(0,edits[0]);drag(row,150,150,950,150);assertEquals(1,edits[0]);drag(row,500,220,500,50);assertEquals(1,edits[0]);assertEquals(1,deletes[0]);
        });
    }
    private static void drag(View row,float x,float y,float xx,float yy){long start=android.os.SystemClock.uptimeMillis();int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};for(int i=0;i<3;i++){MotionEvent event=MotionEvent.obtain(start,start+i*30,actions[i],x+(xx-x)*i/2,y+(yy-y)*i/2,0);row.dispatchTouchEvent(event);event.recycle();}}
}
