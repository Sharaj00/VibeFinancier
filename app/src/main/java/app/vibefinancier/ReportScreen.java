package app.vibefinancier;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.math.*;
import java.util.*;
import org.json.*;

final class ReportScreen {
    interface Actions {void shift(int direction);void period();void filter();void export();void category(String id);void direction(boolean income);}
    static LinearLayout create(Activity a,ReportData data,String base,String from,String to,String period,boolean income,Actions actions){
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls=new LinearLayout(a);controls.setGravity(Gravity.CENTER_VERTICAL);root.addView(controls);
        controls.addView(button(a,period+" ▾",actions::period),new LinearLayout.LayoutParams(0,Ui.dp(a,48),1));controls.addView(button(a,"Filter",actions::filter));controls.addView(button(a,"Export",actions::export));
        LinearLayout totals=new LinearLayout(a);totals.setBackgroundColor(Ui.SURFACE);root.addView(totals);String dates=from+" — "+to;
        BigDecimal selected=income?data.income:data.expense;for(String[] pair:new String[][]{{Dashboard.format(selected,base),income?"Income":"Expenses"},{Dashboard.format(data.income.subtract(data.expense),base),"Net income"}}){LinearLayout cell=Ui.column(a);cell.setPadding(Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8));TextView range=Ui.text(a,dates,10,Ui.MUTED);range.setGravity(Gravity.CENTER);cell.addView(range);TextView value=Ui.text(a,pair[0],21,Ui.INK);value.setGravity(Gravity.CENTER);value.setAutoSizeTextTypeUniformWithConfiguration(12,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);value.setMaxLines(1);cell.addView(value);TextView label=Ui.text(a,pair[1],13,Ui.MUTED);label.setGravity(Gravity.CENTER);cell.addView(label);totals.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}
        Map<String,BigDecimal> groups=income?data.incomes:data.expenses;List<Map.Entry<String,BigDecimal>> rows=new ArrayList<>(groups.entrySet());rows.sort((x,y)->y.getValue().compareTo(x.getValue()));
        LinearLayout graph=new LinearLayout(a);graph.setGravity(Gravity.CENTER_VERTICAL);graph.setBackgroundColor(Ui.SURFACE);root.addView(graph);
        graph.addView(button(a,"‹",()->actions.shift(-1)),new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,64)));
        Donut donut=new Donut(a,rows,data,base,income,id->actions.category(id));graph.addView(donut,new LinearLayout.LayoutParams(0,Ui.dp(a,280),1));graph.addView(button(a,"›",()->actions.shift(1)),new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,64)));
        LinearLayout tabs=new LinearLayout(a);tabs.setBackgroundColor(Ui.SURFACE);root.addView(tabs);Button expenses=button(a,"Expenses",()->actions.direction(false)),incomes=button(a,"Income",()->actions.direction(true));expenses.setTextColor(income?Ui.MUTED:Ui.INK);incomes.setTextColor(income?Ui.INK:Ui.MUTED);tabs.addView(expenses,new LinearLayout.LayoutParams(0,Ui.dp(a,52),1));tabs.addView(incomes,new LinearLayout.LayoutParams(0,Ui.dp(a,52),1));
        if(data.missing>0)root.addView(Ui.text(a,data.missing+" operations need historical exchange rates. Totals are partial.",12,0xffdc9b5c));
        if(rows.isEmpty())root.addView(Ui.text(a,"No operations in this period.",16,Ui.MUTED));
        for(var entry:rows){JSONObject category=data.categories.get(entry.getKey());LinearLayout row=Ui.column(a);row.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12));root.addView(row);
            LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);row.addView(line);CircleIcon icon=new CircleIcon(a,category==null?"percent":IconCatalog.resolve(category),category==null?0xff999999:IconCatalog.color(category));line.addView(icon,new LinearLayout.LayoutParams(Ui.dp(a,42),Ui.dp(a,42)));
            LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(Ui.dp(a,12),0,0,0);line.addView(text,new LinearLayout.LayoutParams(0,-2,1));String name=entry.getKey().equals("__fees__")?"Fees":category==null?"Uncategorized / missing category":category.optString("name");LinearLayout numbers=new LinearLayout(a);numbers.setGravity(Gravity.CENTER_VERTICAL);text.addView(numbers);TextView categoryName=Ui.rawText(a,category==null?Strings.t(a,name):name,16,Ui.INK);categoryName.setMaxLines(2);numbers.addView(categoryName,new LinearLayout.LayoutParams(0,-2,1));
            BigDecimal percent=selected.signum()==0?BigDecimal.ZERO:entry.getValue().multiply(BigDecimal.valueOf(100)).divide(selected,1,RoundingMode.HALF_EVEN);TextView amount=Ui.text(a,Dashboard.format(entry.getValue(),base)+"  ("+percent.stripTrailingZeros().toPlainString()+"%)",14,income?Ui.GREEN:0xffee7777);amount.setGravity(Gravity.END);amount.setMaxLines(2);numbers.addView(amount,new LinearLayout.LayoutParams(0,-2,1));
            ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress(percent.multiply(BigDecimal.TEN).intValue());bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff79d783));text.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(a,5)));row.setContentDescription("Report category "+name);row.setOnClickListener(v->actions.category(entry.getKey()));
        }
        return root;
    }
    private static Button button(Activity a,String text,Runnable action){Button b=Ui.button(a,text,action);b.setBackgroundColor(Color.TRANSPARENT);b.setMinWidth(0);b.setPadding(Ui.dp(a,4),0,Ui.dp(a,4),0);return b;}
    static final class Donut extends View {
        final List<Map.Entry<String,BigDecimal>> rows;final ReportData data;final String base;final BigDecimal total;final java.util.function.Consumer<String> select;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Donut(Activity a,List<Map.Entry<String,BigDecimal>> rows,ReportData data,String base,boolean income,java.util.function.Consumer<String> select){super(a);this.rows=rows;this.data=data;this.base=base;this.total=income?data.income:data.expense;this.select=select;setContentDescription("Category distribution chart");}
        @Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-18,stroke=Math.max(12,r*.11f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(Ui.DIVIDER);RectF bounds=new RectF(cx-r,cy-r,cx+r,cy+r);canvas.drawOval(bounds,paint);float start=-90;for(var row:rows){float sweep=total.signum()<=0?0:row.getValue().divide(total,10,RoundingMode.HALF_EVEN).floatValue()*360;JSONObject category=data.categories.get(row.getKey());paint.setColor(category==null?0xff999999:IconCatalog.color(category));canvas.drawArc(bounds,start,sweep,false,paint);start+=sweep;}paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.INK);paint.setTextAlign(Paint.Align.CENTER);String text=Dashboard.format(total,base);paint.setTextSize(getResources().getDisplayMetrics().scaledDensity*23);while(paint.measureText(text)>r*1.65f&&paint.getTextSize()>12)paint.setTextSize(paint.getTextSize()-1);canvas.drawText(text,cx,cy-(paint.ascent()+paint.descent())/2,paint);}
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_UP)return true;float x=event.getX()-getWidth()/2f,y=event.getY()-getHeight()/2f,r=Math.min(getWidth(),getHeight())/2f-18;if(Math.abs(Math.hypot(x,y)-r)>Math.max(28,r*.2f))return true;double angle=(Math.toDegrees(Math.atan2(y,x))+450)%360;double end=0;for(var row:rows){end+=total.signum()<=0?0:row.getValue().divide(total,10,RoundingMode.HALF_EVEN).doubleValue()*360;if(angle<=end){select.accept(row.getKey());break;}}performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
