package app.vibefinancier;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Lucide SVG icons on native colored circular backgrounds. */
public class CircleIcon extends View {
    private final String icon;
    private int color;
    public void setColor(int color){this.color=color;invalidate();}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public CircleIcon(Context c,String icon,int color){super(c);this.icon=icon;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight());canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/100f,size/100f);
        p.setStyle(Paint.Style.FILL);p.setColor(color);canvas.drawCircle(50,50,49,p);
        int ink=luminance(color)>.36?0xff171b18:0xfff2f2f2;
        if(!SvgIcons.draw(getContext(),canvas,icon,ink)){p.setStyle(Paint.Style.FILL);p.setColor(ink);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(38);canvas.drawText("?",50,64,p);}canvas.restore();
    }
    private static double luminance(int c){return (Color.red(c)*.299+Color.green(c)*.587+Color.blue(c)*.114)/255;}
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,p);}
    private void path(Canvas c,float... coords){Path path=new Path();path.moveTo(coords[0],coords[1]);for(int i=2;i<coords.length;i+=2)path.lineTo(coords[i],coords[i+1]);c.drawPath(path,p);}
}
