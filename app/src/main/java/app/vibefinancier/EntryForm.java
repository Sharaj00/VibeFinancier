package app.vibefinancier;

import android.app.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public class EntryForm {
    public static void show(Activity activity,String kind,JSONObject editing,Runnable saved,Runnable cancelled) {
        show(activity,kind,editing,"","",saved,cancelled);
    }
    public static void show(Activity activity,String kind,JSONObject editing,String selectedAccount,String selectedCategory,Runnable saved,Runnable cancelled) {
        show(activity,kind,editing,selectedAccount,selectedCategory,"",saved,cancelled);
    }
    public static void show(Activity activity,String kind,JSONObject editing,String selectedAccount,String selectedCategory,String selectedTo,Runnable saved,Runnable cancelled) {
        build(activity,new Store(activity),true,kind,editing,selectedAccount,selectedCategory,selectedTo,saved,cancelled);
    }
    static void planned(Activity activity, JSONObject plan, String occurrence, Runnable saved) {
        planned(activity,new Store(activity),true,plan,occurrence,saved);
    }
    static AlertDialog planned(Activity activity, Store store, boolean owned, JSONObject plan, String occurrence, Runnable saved) {
        try {
            String currency=plan.getString("currency"),base=store.get("base","RUB");
            JSONObject quote=Rates.latest(store,currency,base);
            JSONObject seed=Ledger.obj("_planned",true,"amount",plan.getString("amount"),"account",plan.getString("account"),"category",plan.optString("category"),"to",plan.optString("to"),"received_amount",plan.optString("received_amount",plan.optString("amount")),"time",LocalDate.now().atTime(LocalTime.now().withNano(0)).toString(),"note",plan.optString("name")+(plan.optString("note").isEmpty()?"":" · "+plan.optString("note")),"base_currency",base,"rate",currency.equals(base)?"1":quote==null?"":quote.optString("rate"));
            return build(activity,store,owned,plan.getString("payment_kind"),seed,"","","",saved,()->{},plan,occurrence);
        }catch(Exception e){if(owned)store.close();Ui.message(activity,"Cannot open payment",e.getMessage());return null;}
    }
    static AlertDialog build(Activity activity,Store store,boolean owned,String kind,JSONObject editing,String selectedAccount,String selectedCategory,String selectedTo,Runnable saved,Runnable cancelled) {
        return build(activity,store,owned,kind,editing,selectedAccount,selectedCategory,selectedTo,saved,cancelled,null,"");
    }
    private static AlertDialog build(Activity activity,Store store,boolean owned,String kind,JSONObject editing,String selectedAccount,String selectedCategory,String selectedTo,Runnable saved,Runnable cancelled,JSONObject plan,String occurrence) {
        try {
            List<JSONObject> accounts=new ArrayList<>();for(JSONObject p:store.type("account",editing!=null))if(kind.equals("transfer")||!p.optString("account_type").equals("debt"))accounts.add(p);
            if(accounts.isEmpty()){Ui.message(activity,"Create an account first","Open VibeFinancier and add a cash, bank or crypto account.");if(activity instanceof QuickEntryActivity)activity.finish();if(owned)store.close();return null;}
            List<JSONObject> cats=new ArrayList<>();for(JSONObject p:store.type("category",false))if(p.optString("direction").equals(kind))cats.add(p);
            if(editing!=null&&!kind.equals("transfer")&&!editing.optString("category").isEmpty()&&cats.stream().noneMatch(p->p.optString("id").equals(editing.optString("category"))))cats.add(Ledger.obj("id",editing.optString("category"),"name","Keep source category reference"));
            JSONObject usage=new JSONObject(store.get("category_usage","{}"));cats.sort((x,y)->Integer.compare(usage.optInt(y.optString("id")),usage.optInt(x.optString("id"))));
            LinearLayout box=Ui.column(activity);Spinner account=Ui.spinner(activity,box,kind.equals("transfer")?"From account":"Account",Ui.labels(accounts));
            account.setTag("entry.account");
            String last=editing!=null?editing.optString("account"):selectedAccount.isEmpty()?store.get("last_account",""):selectedAccount;for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("id").equals(last))account.setSelection(i);
            if(editing!=null&&accounts.stream().noneMatch(p->p.optString("id").equals(last)))throw new IllegalArgumentException("The source account is missing. Resolve the import mapping before editing this operation.");
            EditText amount=Ui.field(activity,box,"Amount",editing==null?"":editing.optString("amount"),true);amount.setTag("entry.amount");amount.setTextSize(30);amount.setHint("0.00");
            box.addView(Ui.button(activity,"Calculator",()->CalculatorDialog.show(activity,Ui.value(amount),accounts.get(account.getSelectedItemPosition()).optString("currency"),amount::setText)));
            String pickedCategory=editing==null?selectedCategory:editing.optString("category");
            List<JSONObject> roots=new ArrayList<>();for(JSONObject p:cats)if(CategoryChoices.root(p.optString("id"),cats).equals(p.optString("id")))roots.add(p);
            Spinner category=kind.equals("transfer")?null:Ui.spinner(activity,box,"Category",roots.isEmpty()?List.of("Uncategorized"):Ui.labels(roots));
            Spinner subcategory=kind.equals("transfer")?null:Ui.spinner(activity,box,"Subcategory",List.of("No subcategory"));
            if(category!=null)category.setTag("entry.category");if(subcategory!=null)subcategory.setTag("entry.subcategory");
            OperationScreen[] operation={null};
            List<JSONObject> children=new ArrayList<>();
            if(category!=null){String root=CategoryChoices.root(pickedCategory,cats);for(int i=0;i<roots.size();i++)if(roots.get(i).optString("id").equals(root))category.setSelection(i);
                category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){children.clear();List<String> names=new ArrayList<>(List.of("No subcategory"));if(!roots.isEmpty()){String rootId=roots.get(position).optString("id");for(JSONObject c:cats)if(!c.optString("id").equals(rootId)&&CategoryChoices.root(c.optString("id"),cats).equals(rootId)){children.add(c);names.add(CategoryChoices.path(c,cats));}}
                    subcategory.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,names));for(int i=0;i<children.size();i++)if(children.get(i).optString("id").equals(pickedCategory))subcategory.setSelection(i+1);subcategory.setEnabled(!children.isEmpty());if(operation[0]!=null)operation[0].refresh();}});
            }
            Spinner to=kind.equals("transfer")?Ui.spinner(activity,box,"To account / debt counterparty",Ui.labels(accounts)):null;
            if(to!=null)to.setTag("entry.to");
            if(to!=null&&editing==null){String wanted=selectedTo;int candidate=accounts.size()>1?(account.getSelectedItemPosition()+1)%accounts.size():0;for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("id").equals(wanted))candidate=i;to.setSelection(candidate);}
            if(to!=null&&editing!=null){boolean found=false;for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("id").equals(editing.optString("to"))){to.setSelection(i);found=true;}if(!found)throw new IllegalArgumentException("The destination account is missing. Resolve its source mapping before editing.");}
            EditText received=kind.equals("transfer")?Ui.field(activity,box,"Received amount (destination currency)",editing==null?"":editing.optString("received_amount"),true):null;
            EditText fee=Ui.field(activity,box,"Fee (source currency, optional)",editing==null?"0":editing.optString("fee","0"),true);fee.setTag("entry.fee");
            if(received!=null){received.setTag("entry.received");box.addView(Ui.text(activity,"Enter the received amount to derive the execution rate. Source and destination amounts are preserved separately.",12,Ui.MUTED));box.addView(Ui.button(activity,"Calculate received amount from quotes",()->{try{JSONObject a=accounts.get(account.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());String baseCurrency=store.get("base","RUB");JSONObject fromQuote=Rates.latest(store,a.optString("currency"),baseCurrency),toQuote=Rates.latest(store,b.optString("currency"),baseCurrency);if(fromQuote==null||toQuote==null)throw new IllegalArgumentException("Update rates or enter the received amount manually.");received.setText(TransactionExtras.received(Calculator.evaluate(Ui.value(amount),Money.scale(a.optString("currency"))),fromQuote.getString("rate"),toQuote.getString("rate"),b.optString("currency")));}catch(Exception e){received.setError(e.getMessage());}}));}
            RateField[] automaticRate={null};
            LocalDateTime[] selectedTime={editing==null?LocalDateTime.now().withNano(0):LocalDateTime.parse(editing.optString("time"))};
            LinearLayout dateRow=new LinearLayout(activity);Button date=Ui.button(activity,"",()->{}),clock=Ui.button(activity,"",()->{});date.setTag("entry.date");clock.setTag("entry.time");Runnable refreshTime=()->{date.setText(selectedTime[0].toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",Strings.locale(activity))));clock.setText(selectedTime[0].toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));if(automaticRate[0]!=null)automaticRate[0].request(accounts.get(account.getSelectedItemPosition()).optString("currency"),selectedTime[0]);};refreshTime.run();
            date.setOnClickListener(v->{LocalDateTime t=selectedTime[0];new DatePickerDialog(activity,(picker,year,month,day)->{selectedTime[0]=selectedTime[0].withYear(year).withMonth(1).withDayOfMonth(1).withMonth(month+1).withDayOfMonth(day);refreshTime.run();},t.getYear(),t.getMonthValue()-1,t.getDayOfMonth()).show();});
            clock.setOnClickListener(v->{LocalDateTime t=selectedTime[0];new TimePickerDialog(activity,(picker,hour,minute)->{selectedTime[0]=selectedTime[0].withHour(hour).withMinute(minute).withSecond(0);refreshTime.run();},t.getHour(),t.getMinute(),true).show();});
            dateRow.addView(date,new LinearLayout.LayoutParams(0,-2,1));dateRow.addView(clock,new LinearLayout.LayoutParams(0,-2,1));box.addView(dateRow);
            String base=editing==null?store.get("base","RUB"):editing.optString("base_currency",store.get("base","RUB"));EditText rate=Ui.field(activity,box,"Historical rate to "+base+" (1 for same currency)",editing==null?"":editing.optString("rate",""),true);
            android.view.View rateLabel=box.getChildAt(box.indexOfChild(rate)-1);boolean identity=accounts.get(account.getSelectedItemPosition()).optString("currency").equals(base);rateLabel.setVisibility(identity?android.view.View.GONE:android.view.View.VISIBLE);rate.setVisibility(identity?android.view.View.GONE:android.view.View.VISIBLE);
            List<JSONObject> quotes=store.type("rate",true);automaticRate[0]=new RateField(activity,box,rate,base,owned,editing!=null);
            if(editing==null)account.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){String currency=accounts.get(position).optString("currency");rateLabel.setVisibility(currency.equals(base)?android.view.View.GONE:android.view.View.VISIBLE);rate.setVisibility(currency.equals(base)?android.view.View.GONE:android.view.View.VISIBLE);rate.setText(currency.equals(base)?"1":"");for(JSONObject q:quotes)if(q.optString("currency").equals(currency)&&q.optString("base").equals(base))rate.setText(q.optString("rate"));automaticRate[0].request(currency,selectedTime[0]);if(operation[0]!=null)operation[0].refresh();}});
            if(kind.equals("transfer")&&editing==null){amount.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!received.hasFocus()&&accounts.get(account.getSelectedItemPosition()).optString("currency").equals(accounts.get(to.getSelectedItemPosition()).optString("currency"))){try{received.setText(Calculator.evaluate(s.toString(),Money.scale(accounts.get(account.getSelectedItemPosition()).optString("currency"))));}catch(Exception ignored){}}}public void afterTextChanged(android.text.Editable e){}});}
            EditText note=Ui.field(activity,box,"Comment",editing==null?"":editing.optString("note"),false);
            note.setTag("entry.note");note.setSingleLine(false);note.setMinLines(2);note.setMaxLines(4);
            EditText due=kind.equals("transfer")?Ui.field(activity,box,"Debt due date (optional YYYY-MM-DD)",editing==null?"":editing.optString("due"),false):null;
            box.addView(Ui.text(activity,"Saved offline first. Transfers and debt principal do not count as income or expense.",12,Ui.MUTED));
            boolean full=!(activity instanceof QuickEntryActivity);if(category!=null&&category.getOnItemSelectedListener()!=null)category.getOnItemSelectedListener().onItemSelected(category,null,category.getSelectedItemPosition(),0);
            android.view.View content;if(full){operation[0]=new OperationScreen(activity,store,owned,kind,box,amount,dateRow,account,category,to,subcategory,accounts,roots,children,cats);content=operation[0];}else content=Ui.scroll(activity,box);
            AlertDialog dialog=new LocalizedDialog(activity).setTitle(kind.equals("transfer")?"Transfer / debt":kind.equals("expense")?"Add expense":"Add income").setView(content).setNegativeButton("Cancel",(d,w)->cancelled.run()).setPositiveButton("Save",null).create();
            dialog.setView(content,0,0,0,0);dialog.setOnCancelListener(d->cancelled.run());dialog.setOnDismissListener(d->{automaticRate[0].close();if(owned)store.close();});dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{
                try {
                    JSONObject a=accounts.get(account.getSelectedItemPosition());String cur=a.getString("currency");String value=editing!=null&&Ui.value(amount).equals(editing.optString("amount"))?editing.optString("amount"):Money.input(Calculator.evaluate(Ui.value(amount),Money.scale(cur)),cur);String time=selectedTime[0].toString();String usedRate=cur.equals(base)?"1":Ui.value(rate);Money.decimal(usedRate);
                    JSONObject p=Ledger.transaction(kind,a.getString("id"),value,cur,category==null||roots.isEmpty()?"":subcategory.getSelectedItemPosition()>0?children.get(subcategory.getSelectedItemPosition()-1).optString("id"):roots.get(category.getSelectedItemPosition()).optString("id"),Ui.value(note),time,base,usedRate);
                    p.put("timezone",ZoneId.systemDefault().getId());
                    for(JSONObject q:quotes)if(q.optString("currency").equals(cur)&&q.optString("base").equals(base)&&q.optString("rate").equals(usedRate)){p.put("rate_source",q.optString("source"));p.put("rate_time",q.optString("asof"));}
                    automaticRate[0].attach(p,usedRate);
                    if(kind.equals("transfer")){
                        JSONObject b=accounts.get(to.getSelectedItemPosition());if(a.getString("id").equals(b.getString("id")))throw new IllegalArgumentException("Choose two different accounts.");
                        String incoming=Money.input(Ui.value(received),b.getString("currency"));if(Money.decimal(incoming).signum()<=0)throw new IllegalArgumentException("Received amount must be positive.");
                        String fees=Money.input(Ui.value(fee),cur);boolean debt=a.optString("account_type").equals("debt")||b.optString("account_type").equals("debt");p.put("kind",debt?"debt":"transfer");p.put("to",b.getString("id"));p.put("received_amount",incoming);p.put("received_currency",b.getString("currency"));p.put("fee",fees);p.put("fee_base",Money.convert(fees,usedRate,Money.scale(base)));p.put("execution_rate",TransactionExtras.executionRate(value,incoming));p.put("execution_rate_source","received amount");
                        if(!Ui.value(due).isEmpty())LocalDate.parse(Ui.value(due));p.put("due",Ui.value(due));
                        p.put("postings",new JSONArray().put(Ledger.obj("account",a.getString("id"),"currency",cur,"amount",Money.plain(Money.decimal(value).add(Money.decimal(fees)).negate()))).put(Ledger.obj("account",b.getString("id"),"currency",b.getString("currency"),"amount",incoming)));
                        if(debt&&Money.decimal(fees).signum()!=0&&(a.optString("account_type").equals("debt")))throw new IllegalArgumentException("Charge a repayment fee separately on the cash account.");
                    }
                    if(!kind.equals("transfer"))TransactionExtras.fee(p,Ui.value(fee));
                    if(editing!=null&&!editing.optBoolean("_planned")){p.put("id",editing.getString("id"));p.put("_head",editing.getString("_head"));if(editing.has("source"))p.put("source",editing.get("source"));for(String link:List.of("payment_plan","payment_date"))if(editing.has(link))p.put(link,editing.get(link));if(editing.has("rate_before_repair"))p.put("rate_before_repair",editing.get("rate_before_repair"));if(editing.optString("rate").equals(usedRate)){p.put("rate_source",editing.optString("rate_source"));p.put("rate_time",editing.optString("rate_time"));}}
                    if(!owned){if(plan==null)store.edit(p);else if(!store.confirmPayment(plan,occurrence,p))throw new IllegalArgumentException("This payment was already confirmed or skipped.");store.set("last_account",a.getString("id"));dialog.dismiss();saved.run();}else{
                        dialog.setCancelable(false);dialog.getButton(-2).setEnabled(false);dialog.getButton(-1).setEnabled(false);new Thread(()->{try{if(plan==null)store.edit(p);else if(!store.confirmPayment(plan,occurrence,p))throw new IllegalArgumentException("This payment was already confirmed or skipped.");store.set("last_account",a.optString("id"));FinanceWidget.refresh(activity);SyncWorker.enqueue(activity);activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.dismiss();saved.run();});}catch(Exception error){activity.runOnUiThread(()->{if(activity.isDestroyed())return;dialog.setCancelable(true);dialog.getButton(-2).setEnabled(true);dialog.getButton(-1).setEnabled(true);amount.setError(Strings.t(activity,error.getMessage()));});}},"save-operation").start();}
                }catch(Exception e){amount.setError(e.getMessage());}
            });});dialog.show();if(full){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT);dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}amount.requestFocus();return dialog;
        }catch(Exception e){if(owned)store.close();Ui.message(activity,"Cannot open entry",e.getMessage());return null;}
    }
}
