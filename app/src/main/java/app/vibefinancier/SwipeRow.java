package app.vibefinancier;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** Horizontal row gestures coexist with vertical history scrolling. */
final class SwipeRow extends FrameLayout {
    private float downX,downY;private boolean horizontal;private final View content;private final Runnable edit,trash;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    SwipeRow(Context c,View content,Runnable edit,Runnable trash){super(c);this.content=content;this.edit=edit;this.trash=trash;setWillNotDraw(false);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(content,new LayoutParams(-1,-2));setContentDescription("Swipe right to edit, left to delete");}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){content.animate().cancel();content.setTranslationX(0);downX=e.getX();downY=e.getY();horizontal=false;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&Math.abs(dx)>Math.abs(dy)*1.3f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}return false;}
    @Override public boolean onTouchEvent(MotionEvent e){if(!horizontal)return super.onTouchEvent(e);float distance=e.getX()-downX;if(e.getActionMasked()==MotionEvent.ACTION_MOVE){content.setTranslationX(distance);invalidate();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){boolean activate=e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(distance)>Math.max(64*getResources().getDisplayMetrics().density,getWidth()*.24f);content.animate().translationX(0).setDuration(120).start();horizontal=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();if(activate){if(distance>0)edit.run();else trash.run();}return true;}return true;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float offset=content.getTranslationX();if(Math.abs(offset)<1)return;paint.setColor(offset>0?0xff24675a:0xffad4545);c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setColor(Color.WHITE);paint.setTextSize(15*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(offset>0?Paint.Align.LEFT:Paint.Align.RIGHT);c.drawText(Strings.t(getContext(),offset>0?"Edit":"Delete"),offset>0?20:getWidth()-20,getHeight()/2f-(paint.ascent()+paint.descent())/2,paint);}
}
