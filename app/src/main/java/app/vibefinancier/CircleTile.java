package app.vibefinancier;

import android.app.Activity;
import android.graphics.*;
import android.text.*;
import android.view.Gravity;
import android.widget.FrameLayout;

/** Labels use explicit baselines so short tiles remain readable at larger font scales. */
public class CircleTile extends FrameLayout {
    private final Activity activity;
    private final String title,value;
    private final int preferredCircle;private int circle,iconTop;private float labelSize,valueSize,titleHeight;
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean highlighted;
    public void highlight(boolean value){highlighted=value;invalidate();}
    public CircleTile(Activity activity,String title,String value,String icon,int color,int circle){
        super(activity);this.activity=activity;this.title=title;this.value=value;this.preferredCircle=circle;this.circle=circle;setWillNotDraw(false);
        CircleIcon image=new CircleIcon(activity,icon,color);LayoutParams layout=new LayoutParams(Ui.dp(activity,circle),Ui.dp(activity,circle),Gravity.TOP|Gravity.CENTER_HORIZONTAL);layout.topMargin=Ui.dp(activity,27);addView(image,layout);
        text.setColor(Ui.INK);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));text.setTextAlign(Paint.Align.CENTER);setFocusable(true);setContentDescription(title+", "+value);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){int preferred=Ui.dp(activity,27+preferredCircle+43),height=resolveSize(preferred,heightSpec);labelSize=Math.min(sp(12),height*.16f);valueSize=Math.min(sp(13),height*.17f);titleHeight=Math.max(Ui.dp(activity,16),labelSize*1.5f);int valueHeight=(int)Math.ceil(valueSize*2.6f);circle=Math.max(1,Math.min(Ui.dp(activity,preferredCircle),height-(int)titleHeight-valueHeight-Ui.dp(activity,8)));iconTop=(int)titleHeight+Math.max(0,(height-(int)titleHeight-valueHeight-circle-Ui.dp(activity,8))/2);LayoutParams p=(LayoutParams)getChildAt(0).getLayoutParams();p.width=circle;p.height=circle;p.topMargin=iconTop;super.onMeasure(widthSpec,android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(highlighted){Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);border.setColor(Ui.GREEN);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(Ui.dp(activity,3));canvas.drawRoundRect(Ui.dp(activity,2),Ui.dp(activity,2),getWidth()-Ui.dp(activity,2),getHeight()-Ui.dp(activity,2),Ui.dp(activity,12),Ui.dp(activity,12),border);}float available=getWidth()-Ui.dp(activity,6),center=getWidth()/2f;text.setTextSize(labelSize);String label=TextUtils.ellipsize(title,text,available,TextUtils.TruncateAt.END).toString();Paint.FontMetrics fm=text.getFontMetrics();canvas.drawText(label,center,titleHeight/2-(fm.ascent+fm.descent)/2,text);
        text.setTextSize(valueSize);float first=iconTop+circle+Ui.dp(activity,4)-text.getFontMetrics().ascent;
        if(text.measureText(value)<=available)canvas.drawText(value,center,first,text);
        else {int split=value.lastIndexOf(' ');String amount=split>0?value.substring(0,split):value,currency=split>0?value.substring(split+1):"";while(text.measureText(amount)>available&&text.getTextSize()>Math.min(sp(9),valueSize))text.setTextSize(text.getTextSize()-1);canvas.drawText(TextUtils.ellipsize(amount,text,available,TextUtils.TruncateAt.END).toString(),center,first,text);text.setTextSize(Math.min(sp(11),valueSize));canvas.drawText(currency,center,first+valueSize*1.2f,text);}
    }
    private float sp(float value){return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,value,getResources().getDisplayMetrics());}
}
