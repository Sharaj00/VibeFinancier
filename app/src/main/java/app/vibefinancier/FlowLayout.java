package app.vibefinancier;

import android.content.Context;
import android.view.*;

/** Wraps category chips without requiring a fixed number of columns. */
public class FlowLayout extends ViewGroup {
    public FlowLayout(Context context){super(context);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),x=0,y=0,row=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);measureChild(child,widthSpec,heightSpec);if(x+child.getMeasuredWidth()>width&&x>0){y+=row;x=0;row=0;}x+=child.getMeasuredWidth();row=Math.max(row,child.getMeasuredHeight());}setMeasuredDimension(width,resolveSize(y+row,heightSpec));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int width=r-l,x=0,y=0,row=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();if(x+w>width&&x>0){y+=row;x=0;row=0;}child.layout(x,y,x+w,y+h);x+=w;row=Math.max(row,h);}}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
}
