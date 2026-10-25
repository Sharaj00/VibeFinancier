package app.vibefinancier;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import org.json.*;

public class FinanceWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids)manager.updateAppWidget(id,views(c,id));}
    @Override public void onDeleted(Context c,int[] ids){try(Store store=new Store(c)){for(int id:ids)WidgetDraft.remove(store,id);}}
    public static void refresh(Context c){AppWidgetManager manager=AppWidgetManager.getInstance(c);for(int id:manager.getAppWidgetIds(new ComponentName(c,FinanceWidget.class)))manager.updateAppWidget(id,views(c,id));}
    static RemoteViews views(Context c,int id){try(Store store=new Store(c)){return views(c,id,store);}catch(Exception e){return new RemoteViews(c.getPackageName(),R.layout.finance_widget);}}
    static RemoteViews views(Context c,int id,Store store)throws Exception {
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.finance_widget);views.setInt(R.id.widget_root,"setBackgroundColor",Color.TRANSPARENT);int ink=Appearance.light(c)?0xff222522:0xfff3f3f3;
        for(int view:new int[]{R.id.widget_title,R.id.widget_balance,R.id.widget_amount,R.id.widget_from_label,R.id.widget_to_label})views.setTextColor(view,ink);
        JSONObject draft=WidgetDraft.load(store,id),from=null,to=null;for(JSONObject p:store.metadata()){if(p.optBoolean("archived"))continue;if(p.optString("id").equals(draft.optString("account")))from=p;if(p.optString("id").equals(draft.optString("destination")))to=p;}
        views.setTextViewText(R.id.widget_amount,amountText(store,draft));views.setTextViewText(R.id.widget_from_label,from==null?Strings.t(c,"Choose account"):from.optString("name"));views.setTextViewText(R.id.widget_to_label,to==null?Strings.t(c,"Choose to"):to.optString("name"));views.setImageViewBitmap(R.id.widget_from_icon,icon(c,from));views.setImageViewBitmap(R.id.widget_to_icon,icon(c,to));
        views.setTextViewText(R.id.widget_title,"VibeFinancier");views.setTextColor(R.id.widget_title,Appearance.light(c)?0xff606660:0xffb4b8b4);views.setTextColor(R.id.widget_save,0xff8bc9b7);
        views.setViewVisibility(R.id.widget_title,android.view.View.VISIBLE);boolean privateBalance=store.get("widget_private","true").equals("true");views.setViewVisibility(R.id.widget_balance,privateBalance?android.view.View.GONE:android.view.View.VISIBLE);views.setTextViewText(R.id.widget_balance,privateBalance?"":"Balance "+store.get("widget_balance","—"));
        activity(c,views,id,R.id.widget_from_icon,"source");activity(c,views,id,R.id.widget_from_label,"source");activity(c,views,id,R.id.widget_amount,"amount");activity(c,views,id,R.id.widget_to_label,"destination");activity(c,views,id,R.id.widget_save,"save");Intent launch=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);views.setOnClickPendingIntent(R.id.widget_title,PendingIntent.getActivity(c,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        activity(c,views,id,R.id.widget_to_icon,"destination");return views;
    }
    private static void activity(Context c,RemoteViews views,int widget,int view,String part){Intent intent=new Intent(c,WidgetDraftActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra("widget",widget).putExtra("part",part).setData(android.net.Uri.parse("vibefinancier://widget/"+widget+"/"+part));views.setOnClickPendingIntent(view,PendingIntent.getActivity(c,widget,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));}
    private static String amountText(Store store,JSONObject draft)throws Exception {if(draft.optBoolean("editing"))return draft.optString("expression").isEmpty()?"0":draft.optString("expression");String currency;try{currency=WidgetDraft.entity(store,draft.optString("account")).getString("currency");}catch(Exception e){currency=store.get("base","RUB");}return (draft.optString("amount").isEmpty()?"0":draft.optString("amount"))+" "+currency;}
    static void refreshAmount(Context c,int id,Store store)throws Exception {RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.finance_widget);views.setTextViewText(R.id.widget_amount,amountText(store,WidgetDraft.load(store,id)));AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(id,views);}
    private static Bitmap icon(Context c,JSONObject entity){int size=(int)(52*c.getResources().getDisplayMetrics().density);CircleIcon view=new CircleIcon(c,entity==null?"plus":IconCatalog.resolve(entity),entity==null?0xff858585:IconCatalog.color(entity));view.measure(android.view.View.MeasureSpec.makeMeasureSpec(size,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(size,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,size,size);Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));return image;}
}
