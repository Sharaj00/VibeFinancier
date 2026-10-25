package app.vibefinancier;

import android.content.ClipData;
import android.graphics.Rect;
import android.view.*;
import org.json.JSONObject;

/** Scrolling wins until an eligible icon has been held still for 200 ms. */
final class ImmediateDrag {
    static final long HOLD_MS=200;
    static void install(View tile,JSONObject entity){
        tile.setOnLongClickListener(null);tile.setLongClickable(false);
        if(!DragOperation.canStart(entity)){tile.setOnTouchListener(null);return;}
        float[] down=new float[2];boolean[] armed={false},dragged={false};int slop=ViewConfiguration.get(tile.getContext()).getScaledTouchSlop();
        Runnable begin=()->{if(!armed[0]||!tile.isAttachedToWindow())return;armed[0]=false;tile.setPressed(false);tile.getParent().requestDisallowInterceptTouchEvent(true);dragged[0]=tile.startDragAndDrop(ClipData.newPlainText("VibeFinancier","New operation"),new View.DragShadowBuilder(tile),entity,0);if(dragged[0])tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);else tile.getParent().requestDisallowInterceptTouchEvent(false);};
        tile.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){v.removeCallbacks(begin);armed[0]=false;}});
        tile.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{v.removeCallbacks(begin);Rect icon=new Rect();if(v instanceof ViewGroup group&&group.getChildCount()>0)group.getChildAt(0).getHitRect(icon);armed[0]=icon.contains((int)event.getX(),(int)event.getY());dragged[0]=false;down[0]=event.getX();down[1]=event.getY();if(armed[0])v.postDelayed(begin,HOLD_MS);}
                case MotionEvent.ACTION_MOVE->{if(armed[0]&&Math.hypot(event.getX()-down[0],event.getY()-down[1])>slop){armed[0]=false;v.removeCallbacks(begin);}}
                case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{v.removeCallbacks(begin);armed[0]=false;boolean consume=dragged[0];dragged[0]=false;if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);return consume;}
            }
            return dragged[0];
        });
    }
}
