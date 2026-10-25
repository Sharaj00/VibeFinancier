package app.vibefinancier;

import org.json.JSONObject;
import java.util.*;

/** Exact expression state; unfinished input never becomes a financial record. */
final class WidgetInput {
    private WidgetInput(){}
    static void expression(Store store,int widget,String expression)throws Exception {
        if(expression.length()>256||!expression.matches("[0-9.,+*/()\\- ×÷−]*"))throw new IllegalArgumentException("Enter numbers and arithmetic signs only (up to 256 characters).");
        JSONObject draft=WidgetDraft.load(store,widget);draft.put("expression",expression);draft.put("editing",true);draft.remove("input_error");WidgetDraft.update(store,widget,draft);
    }
    static void finish(Store store,int widget)throws Exception {
        JSONObject draft=WidgetDraft.load(store,widget);String expression=draft.optString("expression",draft.optString("amount"));
        String currency=WidgetDraft.entity(store,draft.optString("account")).getString("currency");
        String result=expression.isEmpty()?"":Calculator.evaluate(expression,Money.scale(currency));
        if(!result.isEmpty()&&Money.decimal(result).signum()<0)throw new IllegalArgumentException("Use a positive payment amount.");
        draft.put("amount",result);draft.put("expression","");draft.put("editing",false);draft.remove("input_error");WidgetDraft.update(store,widget,draft);
    }
    static void cancel(Store store,int widget)throws Exception {JSONObject draft=WidgetDraft.load(store,widget);draft.put("editing",false);draft.put("expression","");WidgetDraft.update(store,widget,draft);}
    static List<JSONObject> level(List<JSONObject> entities,boolean source,String mode,String parent,String from){
        List<JSONObject> result=new ArrayList<>();Set<String> categoryIds=new HashSet<>();
        for(JSONObject p:entities)if(p.optString("entity").equals("category")&&!p.optBoolean("archived"))categoryIds.add(p.optString("id"));
        for(JSONObject p:WidgetDraft.choices(entities,source,mode)){
            boolean account=p.optString("entity").equals("account");
            if(source){result.add(p);continue;}
            if(account){if(parent.isEmpty()&&!p.optString("id").equals(from))result.add(p);}
            else if(parent.isEmpty()?p.optString("parent").isEmpty()||!categoryIds.contains(p.optString("parent")):p.optString("parent").equals(parent))result.add(p);
        }
        return result;
    }
}
