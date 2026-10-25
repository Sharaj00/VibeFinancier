package app.vibefinancier;

import android.app.Activity;
import android.widget.*;
import org.json.*;
import java.time.*;

/** Background quote lookup cannot overwrite a manual field or a newer date selection. */
final class RateField {
    private final Activity activity;private final EditText field;private final TextView status;private final CheckBox automatic;private final String base;private int generation;private boolean active=true,pending;
    JSONObject quote;private String currency="";private LocalDateTime date;
    RateField(Activity a,LinearLayout parent,EditText field,String base,boolean enabled,boolean editing){activity=a;this.field=field;this.base=base;automatic=new CheckBox(a);automatic.setText(Strings.t(a,"Automatic reference rate"));automatic.setChecked(enabled&&!editing);parent.addView(automatic);status=Ui.text(a,"Saved quotes work offline. Check the source and quote time before saving.",11,Ui.MUTED);parent.addView(status);field.setEnabled(!automatic.isChecked());automatic.setOnCheckedChangeListener((b,on)->{field.setEnabled(!on);generation++;pending=false;if(on&&!currency.isEmpty())request(currency,date);});}
    void close(){active=false;generation++;}
    void request(String currency,LocalDateTime date){if(pending&&currency.equals(this.currency)&&date.equals(this.date))return;this.currency=currency;this.date=date;automatic.setVisibility(currency.equals(base)?android.view.View.GONE:android.view.View.VISIBLE);int request=++generation;if(!automatic.isChecked())return;
        if(currency.equals(base)){quote=Ledger.obj("rate","1","source","identity","asof",date.toString());field.setText("1");status.setText(Strings.t(activity,"Same currency"));return;}
        pending=true;quote=null;field.setText("");status.setText(Strings.t(activity,"Loading reference quote…"));
        new Thread(()->{JSONObject result=null;String error="";try(Store lookup=new Store(activity)){
            JSONObject cached=Rates.latest(lookup,currency,base);if(cached!=null&&cached.optBoolean("manual")){result=cached;}else if(date.isAfter(LocalDateTime.now().minusMinutes(15))){try{Rates.update(lookup);}catch(Exception e){error=e.getMessage();}result=Rates.latest(lookup,currency,base);}else{try{result=Rates.atTime(currency,base,date,ZoneId.systemDefault());Rates.save(lookup,java.util.List.of(result),false);}catch(Exception e){error=e.getMessage();for(JSONObject p:lookup.type("quote",true))if(cachedForTime(p,currency,date)){result=p;break;}}}
        }catch(Exception e){error=e.getMessage();}JSONObject value=result;String problem=error;activity.runOnUiThread(()->{if(!active||activity.isDestroyed()||request!=generation||!automatic.isChecked())return;pending=false;quote=value;if(value!=null){field.setText(value.optString("rate"));status.setText(Strings.t(activity,value.optString("source")+" · "+value.optString("asof")+(problem==null||problem.isEmpty()?"":"\nCached quote · "+problem)));}else status.setText(Strings.t(activity,"Quote unavailable. Enter a verified manual rate.")+"\n"+problem);});},"entry-quote").start();
    }
    void attach(JSONObject tx,String usedRate)throws Exception {if(quote!=null&&quote.optString("rate").equals(usedRate)){tx.put("rate_source",quote.optString("source"));tx.put("rate_time",quote.optString("asof"));tx.put("rate_granularity",quote.optString("granularity","provider observation"));}}
    private boolean cachedForTime(JSONObject p,String currency,LocalDateTime time){if(!p.optString("currency").equals(currency)||!p.optString("base").equals(base))return false;if(!Rates.CRYPTO.containsKey(currency))return p.optString("asof").startsWith(time.toLocalDate().toString());try{long age=time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()-Instant.parse(p.getString("asof")).toEpochMilli();return age>=0&&age<=7200_000;}catch(Exception e){return false;}}
}
