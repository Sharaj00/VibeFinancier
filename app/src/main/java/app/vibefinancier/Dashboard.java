package app.vibefinancier;

import android.app.Activity;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Four independent horizontal bands keep the finance overview close to ProCoins. */
public class Dashboard extends LinearLayout {
    public interface Actions {void category(JSONObject p);void account(JSONObject p);void edit(JSONObject p);void add(String group);void statistics();default void operation(JSONObject route){}}
    private final Activity activity;
    private final Actions actions;
    private final String base;
    private final Map<String,BigDecimal> balances;
    private final Map<String,BigDecimal> amounts;
    private final GridLayout groups;private final List<LinearLayout> rows=new ArrayList<>();private int columns=0;
    private static final int[] COLORS={0xffe8bd47,0xff82b26c,0xffd17459,0xffa59bb0,0xfff1d265,0xffe27676,0xff79b8bc,0xffe5a35e};
    public Dashboard(Activity a,List<JSONObject> entities,Map<String,BigDecimal> balances,Map<String,BigDecimal> amounts,String base,BigDecimal total,BigDecimal income,BigDecimal expense,String period,Actions actions){
        super(a);this.activity=a;this.actions=actions;this.base=base;this.balances=balances;this.amounts=amounts;setOrientation(VERTICAL);setBackgroundColor(Ui.BG);
        LinearLayout overview=new LinearLayout(a);overview.setBackgroundColor(Ui.SURFACE);overview.setPadding(0,Ui.dp(a,6),0,Ui.dp(a,6));
        summary(overview,"Balance",total,actions::statistics);summary(overview,"Expenses",expense,actions::statistics);summary(overview,"Income",income,actions::statistics);addView(overview);
        TextView dates=Ui.text(a,period,11,Ui.MUTED);dates.setGravity(Gravity.CENTER);dates.setPadding(0,Ui.dp(a,4),0,Ui.dp(a,4));addView(dates);
        List<JSONObject> incomes=new ArrayList<>(),accounts=new ArrayList<>(),expenses=new ArrayList<>(),debts=new ArrayList<>();
        for(JSONObject p:entities){if(p.optBoolean("archived"))continue;String type=p.optString("entity");if(type.equals("account")){if(p.optString("account_type").equals("debt"))debts.add(p);else accounts.add(p);}else if(type.equals("category")&&p.optString("parent").isEmpty()){if(p.optString("direction").equals("income"))incomes.add(p);else expenses.add(p);}}
        groups=new GridLayout(a);groups.setUseDefaultMargins(false);addView(groups,new LayoutParams(-1,0,1));
        band("Income",incomes,"income");band("Accounts",accounts,"account");band("Expenses",expenses,"expense");band("Debts",debts,"debt");
    }
    private void summary(LinearLayout parent,String label,BigDecimal value,Runnable click){LinearLayout box=new LinearLayout(activity);box.setOrientation(VERTICAL);box.setGravity(Gravity.CENTER);TextView name=Ui.text(activity,label,14,Ui.INK);name.setTypeface(null,android.graphics.Typeface.BOLD);box.addView(name);TextView money=Ui.text(activity,format(value,base),18,Ui.INK);money.setSingleLine(true);money.setAutoSizeTextTypeUniformWithConfiguration(10,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);money.setEllipsize(TextUtils.TruncateAt.END);box.addView(money);box.setContentDescription(label+": "+format(value,base));box.setOnClickListener(v->click.run());parent.addView(box,new LayoutParams(0,Ui.dp(activity,65),1));}
    private void band(String name,List<JSONObject> entities,String group){
        LinearLayout section=new LinearLayout(activity);section.setOrientation(VERTICAL);section.setPadding(Ui.dp(activity,3),Ui.dp(activity,3),Ui.dp(activity,3),Ui.dp(activity,3));View divider=new View(activity);divider.setBackgroundColor(Ui.DIVIDER);section.addView(divider,new LayoutParams(-1,Ui.dp(activity,1)));
        TextView heading=Ui.text(activity,name,11,Ui.MUTED);heading.setPadding(Ui.dp(activity,10),0,0,Ui.dp(activity,4));section.addView(heading);
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);LinearLayout row=new LinearLayout(activity);rows.add(row);scroll.setTag("dashboard.row."+group);
        int width=Math.max(68,(int)(activity.getResources().getDisplayMetrics().widthPixels/activity.getResources().getDisplayMetrics().density)/5),circle=Math.min(72,width-16);
        int i=0;for(JSONObject p:entities){String title=p.optString("name");boolean account=group.equals("account")||group.equals("debt");String currency=account?p.optString("display_currency",p.optString("currency")):base;BigDecimal amount=(account?balances:amounts).getOrDefault(p.optString("id"),BigDecimal.ZERO);String icon=p.optString("icon",account?p.optString("account_type","wallet"):group.equals("income")?"coins":"cart");int color=color(p,i++);
            View tile=tile(title,(p.optBoolean("valuation_missing")?"No quote · ":"")+format(amount,currency),IconCatalog.resolve(p),color,width,circle);tile.setTag(p);tile.setOnClickListener(v->{if(account)actions.account(p);else actions.category(p);});ImmediateDrag.install(tile,p);
            tile.setOnDragListener((v,e)->{if(!(e.getLocalState() instanceof JSONObject source))return false;JSONObject route=DragOperation.resolve(source,p);boolean valid=route!=null;if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return valid;switch(e.getAction()){case DragEvent.ACTION_DRAG_ENTERED->((CircleTile)v).highlight(valid);case DragEvent.ACTION_DRAG_LOCATION->{int[] point=new int[2];v.getLocationOnScreen(point);int[] edge=new int[2];scroll.getLocationOnScreen(edge);float x=point[0]+e.getX()-edge[0];if(x<Ui.dp(activity,32))scroll.smoothScrollBy(-Ui.dp(activity,24),0);else if(x>scroll.getWidth()-Ui.dp(activity,32))scroll.smoothScrollBy(Ui.dp(activity,24),0);}case DragEvent.ACTION_DROP->{((CircleTile)v).highlight(false);if(valid){v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);actions.operation(route);}return valid;}case DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->((CircleTile)v).highlight(false);}return valid;});
            tile.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01020001,"Edit "+title));}@Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle args){if(action==0x01020001){actions.edit(p);return true;}return super.performAccessibilityAction(host,action,args);}});
            row.addView(tile,new LayoutParams(Ui.dp(activity,width),-1));}
        View plus=tile("",entities.isEmpty()?"Add "+name.toLowerCase(Locale.ROOT):"Add", "plus",Appearance.light(activity)?0xffe1e3e1:0xff292929,width,circle);plus.setContentDescription("Add "+group);plus.setOnClickListener(v->actions.add(group));row.addView(plus,new LayoutParams(Ui.dp(activity,width),-1));
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));section.addView(scroll,new LayoutParams(-1,0,1));
        groups.addView(section);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?activity.getResources().getDisplayMetrics().heightPixels-Ui.dp(activity,100):MeasureSpec.getSize(heightSpec),width=MeasureSpec.getSize(widthSpec);int next=width>height*1.3f||height<Ui.dp(activity,440)?2:1;
        if(columns!=next){columns=next;groups.setColumnCount(GridLayout.UNDEFINED);groups.setRowCount(GridLayout.UNDEFINED);for(int i=0;i<groups.getChildCount();i++){GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(i/columns,GridLayout.FILL,1f),GridLayout.spec(i%columns,GridLayout.FILL,1f));p.width=0;p.height=0;groups.getChildAt(i).setLayoutParams(p);}groups.setColumnCount(columns);groups.setRowCount(4/columns);}
        int tileWidth=Math.max(Ui.dp(activity,64),(width/columns-Ui.dp(activity,6))/5);for(LinearLayout row:rows)for(int i=0;i<row.getChildCount();i++){View tile=row.getChildAt(i);android.view.ViewGroup.LayoutParams p=tile.getLayoutParams();if(p.width!=tileWidth){p.width=tileWidth;tile.setLayoutParams(p);}}
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
    private View tile(String title,String amount,String icon,int color,int width,int circle){return new CircleTile(activity,title,amount,icon,color,circle);}
    private int color(JSONObject p,int index){if(p.has("color"))return p.optInt("color",COLORS[index%COLORS.length]);JSONObject source=p.optJSONObject("source");if(source!=null){JSONArray raw=source.optJSONArray("raw");if(raw!=null&&source.optString("section").equals("category")&&raw.length()>7)try{int color=Integer.parseInt(raw.getString(7));if(Color.alpha(color)!=0)return color;}catch(Exception ignored){}}
        return COLORS[index%COLORS.length];}
    public static String format(BigDecimal amount,String currency){String value=Money.plain(amount),symbol=switch(currency){case "RUB"->"₽";case "USD"->"$";case "EUR"->"€";default->currency;};String[] parts=value.split("\\.",2);String integer=parts[0].replaceAll("(?<=\\d)(?=(\\d{3})+$)"," ");return integer+(parts.length>1?"."+parts[1]:"")+" "+symbol;}
}
