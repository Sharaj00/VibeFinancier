package app.vibefinancier;

import android.app.*;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public final class AccountForm {
    private AccountForm(){}
    public static AlertDialog show(Activity activity,Store store,List<JSONObject> snapshot,JSONObject old,String initialType,Runnable saved)throws Exception {
        LinearLayout box=Ui.column(activity);JSONObject initial=old==null?Ledger.obj("entity","account","account_type",initialType):old;IconPicker visual=new IconPicker(activity,IconCatalog.resolve(initial),IconCatalog.color(initial));box.addView(visual);
        EditText name=Ui.field(activity,box,"Account name",old==null?"":old.optString("name"),false);
        List<String> kinds=List.of("cash","bank_card","savings","crypto","debt","other");Spinner kind=Ui.spinner(activity,box,"Account type",List.of("Cash","Bank card","Savings","Crypto wallet","Debt counterparty","Other"));kind.setSelection(Math.max(0,kinds.indexOf(initial.optString("account_type"))));
        EditText currency=Ui.field(activity,box,"Currency",old==null?store.get("base","RUB"):old.optString("currency"),false);currency.setEnabled(old==null);
        BigDecimal previous=old==null?BigDecimal.ZERO:Ledger.balances(snapshot).getOrDefault(old.optString("id"),BigDecimal.ZERO);
        EditText balance=Ui.field(activity,box,old==null?"Opening balance":"Current balance",Money.plain(previous),true);
        CheckBox excluded=new CheckBox(activity);excluded.setText(Strings.t(activity,"Exclude from total balance"));excluded.setChecked(old!=null&&old.optBoolean("exclude_from_total"));box.addView(excluded);
        CheckBox archived=new CheckBox(activity);archived.setText(Strings.t(activity,"Archived · keep account history"));archived.setChecked(old!=null&&old.optBoolean("archived"));box.addView(archived);
        box.addView(Ui.text(activity,old==null?"An opening balance is separate from income.":"Changing the balance records an adjustment. Existing operations stay unchanged.",12,Ui.MUTED));
        AlertDialog dialog=new LocalizedDialog(activity).setTitle(old==null?"New account":"Edit account").setView(Ui.scroll(activity,box)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            if(Ui.value(name).isEmpty())throw new IllegalArgumentException("Enter an account name.");String cur=Ui.value(currency).toUpperCase(Locale.ROOT);Money.scale(cur);
            JSONObject account=old==null?Ledger.obj("id",UUID.randomUUID().toString(),"entity","account"):new JSONObject(old.toString());account.put("name",Ui.value(name));account.put("currency",cur);account.put("account_type",kinds.get(kind.getSelectedItemPosition()));account.put("icon",visual.icon());if(visual.svg().isEmpty())account.remove("icon_svg");else account.put("icon_svg",visual.svg());account.put("color",visual.color());account.put("exclude_from_total",excluded.isChecked());account.put("archived",archived.isChecked());
            List<JSONObject> events=AccountEdits.events(account,previous,Ui.value(balance),old==null,LocalDateTime.now().withNano(0).toString());if(old!=null&&!old.optString("account_type").equals(account.optString("account_type"))){List<JSONObject> values=new ArrayList<>(snapshot);for(int i=0;i<values.size();i++)if(values.get(i).optString("id").equals(account.optString("id")))values.set(i,account);String parent=account.optString("parent");for(JSONObject q:values)if(q.optString("id").equals(parent)&&!q.optString("account_type").equals(account.optString("account_type")))parent="";List<JSONObject> moves=EntityHierarchy.move(account,parent,account.optString("account_type"),values);moves.addAll(events.subList(1,events.size()));events=moves;}List<JSONObject> commits=events;dialog.setCancelable(false);dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);new Thread(()->{try{store.merge(commits,true);FinanceWidget.refresh(activity);SyncWorker.enqueue(activity);activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();saved.run();});}catch(Exception error){activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.setCancelable(true);dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);name.setError(Strings.t(activity,error.getMessage()));});}},"save-account").start();
        }catch(Exception e){name.setError(e.getMessage());}}));dialog.show();return dialog;
    }
}
