package app.vibefinancier;

import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

final class CategoryScreen {
    interface Actions {void edit(JSONObject item);void move(JSONObject item);void archive(JSONObject item);void add(String group);}
    static void create(Activity a,LinearLayout body,List<JSONObject> values,Set<String> expanded,Actions actions){
        body.addView(Ui.text(a,"Tap to expand · swipe right to edit · swipe left to archive",12,Ui.MUTED));
        for(String group:List.of("account","debt","expense","income")){
            LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(Ui.text(a,switch(group){case "account"->"Accounts";case "debt"->"Debts";case "expense"->"Expenses";default->"Income";},19,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));Button add=Ui.button(a,"+",()->actions.add(group));add.setBackgroundColor(android.graphics.Color.TRANSPARENT);header.addView(add);body.addView(header);
            Set<String> shown=new HashSet<>();for(JSONObject p:values){if(!matches(p,group)||p.optBoolean("archived"))continue;boolean parentExists=false;for(JSONObject q:values)if(q.optString("id").equals(p.optString("parent"))&&matches(q,group)&&!q.optBoolean("archived"))parentExists=true;if(!parentExists)row(a,body,p,values,expanded,actions,0,shown);}
        }
    }
    private static boolean matches(JSONObject p,String group){return group.equals("account")||group.equals("debt")?p.optString("entity").equals("account")&&p.optString("account_type").equals("debt")==group.equals("debt"):p.optString("entity").equals("category")&&p.optString("direction").equals(group);}
    private static void row(Activity a,LinearLayout body,JSONObject p,List<JSONObject> values,Set<String> expanded,Actions actions,int depth,Set<String> shown){
        if(!shown.add(p.optString("id")))return;List<JSONObject> children=new ArrayList<>();for(JSONObject child:values)if(child.optString("entity").equals(p.optString("entity"))&&child.optString("parent").equals(p.optString("id"))&&!child.optBoolean("archived"))children.add(child);
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(a,Math.min(depth,5)*14+6),Ui.dp(a,8),Ui.dp(a,4),Ui.dp(a,8));row.addView(new CircleIcon(a,IconCatalog.resolve(p),IconCatalog.color(p)),new LinearLayout.LayoutParams(Ui.dp(a,42),Ui.dp(a,42)));
        TextView name=Ui.rawText(a,p.optString("name")+(children.isEmpty()?"":expanded.contains(p.optString("id"))?"  ▾":"  ▸"),16,Ui.INK);name.setPadding(Ui.dp(a,10),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button move=Ui.button(a,"Move",()->actions.move(p));move.setBackgroundColor(android.graphics.Color.TRANSPARENT);row.addView(move);
        row.setTag("category."+p.optString("id"));row.setOnClickListener(v->{if(children.isEmpty()){actions.edit(p);return;}String id=p.optString("id");if(!expanded.add(id))expanded.remove(id);body.removeAllViews();create(a,body,values,expanded,actions);});
        body.addView(new SwipeRow(a,row,()->actions.edit(p),()->actions.archive(p)),new LinearLayout.LayoutParams(-1,-2));if(expanded.contains(p.optString("id")))for(JSONObject child:children)row(a,body,child,values,expanded,actions,depth+1,shown);
    }
}
