package app.vibefinancier;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Calendar and plan editor; choosing a date never writes a financial operation. */
final class PaymentScreen {
    static void show(Activity a,LinearLayout parent,Store store,List<JSONObject> entities,Runnable changed) {
        LinearLayout content=Ui.column(a);parent.addView(content);
        LocalDate[] selected={LocalDate.now()};YearMonth[] month={YearMonth.now()};
        Runnable[] render={null};render[0]=()->{
            content.removeAllViews();
            LinearLayout nav=new LinearLayout(a);nav.setGravity(Gravity.CENTER_VERTICAL);
            nav.addView(Ui.button(a,"‹",()->{month[0]=month[0].minusMonths(1);selected[0]=month[0].atDay(1);render[0].run();}));
            TextView title=Ui.rawText(a,month[0].atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy",Strings.locale(a))),20,Ui.INK);title.setGravity(Gravity.CENTER);nav.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            nav.addView(Ui.button(a,"›",()->{month[0]=month[0].plusMonths(1);selected[0]=month[0].atDay(1);render[0].run();}));content.addView(nav);
            Set<String> done=Payments.completed(entities);Map<LocalDate,List<JSONObject>> due=new TreeMap<>();List<JSONObject> plans=new ArrayList<>();
            for(JSONObject p:entities)if(p.optString("entity").equals("payment_plan")){plans.add(p);for(LocalDate d:Payments.dates(p,month[0].atDay(1),month[0].atEndOfMonth()))due.computeIfAbsent(d,k->new ArrayList<>()).add(p);}
            GridLayout grid=new GridLayout(a);grid.setColumnCount(7);int offset=month[0].atDay(1).getDayOfWeek().getValue()-1;
            for(int i=0;i<7;i++){TextView label=Ui.rawText(a,java.time.DayOfWeek.of(i+1).getDisplayName(java.time.format.TextStyle.SHORT,Strings.locale(a)),11,Ui.MUTED);label.setGravity(Gravity.CENTER);cell(grid,label,i,0,a);}
            for(int i=0;i<offset;i++)cell(grid,new View(a),i,1,a);
            for(int day=1;day<=month[0].lengthOfMonth();day++){
                LocalDate d=month[0].atDay(day);boolean pending=false;for(JSONObject plan:due.getOrDefault(d,List.of()))if(!done.contains(Payments.occurrence(plan,d)))pending=true;
                TextView button=Ui.rawText(a,day+(pending?" •":""),14,d.equals(selected[0])?Ui.GREEN:Ui.INK);button.setGravity(Gravity.CENTER);if(d.equals(selected[0]))button.setBackgroundColor(Ui.SURFACE);button.setContentDescription(d.toString());button.setOnClickListener(v->{selected[0]=d;render[0].run();});cell(grid,button,(offset+day-1)%7,1+(offset+day-1)/7,a);
            }
            content.addView(grid);content.addView(Ui.button(a,"Add planned payment",()->edit(a,store,null,changed)));
            content.addView(Ui.button(a,"Manage payment plans",()->manage(a,store,plans,changed)));
            boolean enabled=a.getSharedPreferences("payment-reminders",0).getBoolean("enabled",false);
            content.addView(Ui.button(a,enabled?"Reminder settings":"Enable payment reminders",()->reminders(a)));
            content.addView(Ui.text(a,"Plans do not change balances. Confirm each payment after it happens.",12,Ui.MUTED));
            content.addView(Ui.rawText(a,selected[0].format(DateTimeFormatter.ofPattern("d MMMM yyyy",Strings.locale(a))),19,Ui.INK));
            List<JSONObject> todays=due.getOrDefault(selected[0],List.of());if(todays.isEmpty())content.addView(Ui.text(a,"No planned payments",14,Ui.MUTED));
            for(JSONObject plan:todays){LocalDate d=selected[0];String id=Payments.occurrence(plan,d);LinearLayout card=Ui.card(a,content);card.addView(Ui.rawText(a,plan.optString("name"),19,Ui.INK));card.addView(Ui.rawText(a,plan.optString("amount")+" "+plan.optString("currency"),22,Ui.INK));card.addView(Ui.text(a,plan.optString("frequency"),12,Ui.MUTED));
                if(done.contains(id)){String status="Completed / skipped";for(JSONObject r:entities)if(r.optString("id").equals(id+".transaction"))status="Confirmed";card.addView(Ui.text(a,status,14,Ui.GREEN));}
                else {Button confirm=Ui.button(a,"Confirm payment",()->EntryForm.planned(a,plan,d.toString(),changed));confirm.setTag("payment.confirm");card.addView(confirm);card.addView(Ui.button(a,"Skip this occurrence",()->new LocalizedDialog(a).setTitle("Skip this occurrence?").setMessage("No operation will be created.").setNegativeButton("Cancel",null).setPositiveButton("Skip",(dialog,w)->mutate(a,()->store.confirmPayment(plan,d.toString(),null),changed)).show()));}
                card.addView(Ui.button(a,"Edit plan",()->edit(a,store,plan,changed)));
            }
            // Overdue items stay visible independently of the selected calendar month.
            LocalDate today=LocalDate.now();List<JSONObject> overduePlans=new ArrayList<>();List<LocalDate> overdueDates=new ArrayList<>();
            for(JSONObject plan:plans){for(LocalDate d:Payments.dates(plan,LocalDate.parse(plan.optString("start")),today.minusDays(1)))if(!done.contains(Payments.occurrence(plan,d))){overduePlans.add(plan);overdueDates.add(d);break;}}
            if(!overduePlans.isEmpty()){content.addView(Ui.text(a,"Overdue payments",19,Ui.INK));for(int i=0;i<overduePlans.size();i++){JSONObject plan=overduePlans.get(i);LocalDate d=overdueDates.get(i);content.addView(Ui.button(a,plan.optString("name")+" · "+d,()->{month[0]=YearMonth.from(d);selected[0]=d;render[0].run();}));}}
        };render[0].run();
    }
    private static void cell(GridLayout grid,View child,int col,int row,Activity a){GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(row),GridLayout.spec(col,1f));p.width=0;p.height=Ui.dp(a,42);grid.addView(child,p);}
    private interface Change{void run()throws Exception;}
    private static void mutate(Activity a,Change work,Runnable changed){mutate(a,work,changed,()->{});}
    private static void mutate(Activity a,Change work,Runnable changed,Runnable failed){new Thread(()->{try{work.run();SyncWorker.enqueue(a);PaymentReminderWorker.schedule(a);a.runOnUiThread(()->{if(!a.isDestroyed())changed.run();});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()){failed.run();Ui.message(a,"Payment",e.getMessage());}});}},"payment-save").start();}
    private static void manage(Activity a,Store store,List<JSONObject> plans,Runnable changed){
        String[] names=plans.stream().map(p->p.optString("name")+(p.optBoolean("archived")?" · "+Strings.t(a,"Archived"):"")).toArray(String[]::new);
        new LocalizedDialog(a).setTitle("Manage payment plans").setItems(names,(d,w)->edit(a,store,plans.get(w),changed)).setNegativeButton("Cancel",null).show();
    }
    static void edit(Activity a,Store store,JSONObject old,Runnable changed){
        try {
            List<JSONObject> accounts=new ArrayList<>(),targets=new ArrayList<>(),categories=new ArrayList<>();
            for(JSONObject p:store.type("account",false)){targets.add(p);if(!p.optString("account_type").equals("debt"))accounts.add(p);}for(JSONObject p:store.type("category",false))if(p.optString("direction").equals("expense"))categories.add(p);
            if(accounts.isEmpty()){Ui.message(a,"Create an account first","Add an account before planning payments.");return;}
            LinearLayout box=Ui.column(a);EditText name=Ui.field(a,box,"Payment name",old==null?"":old.optString("name"),false);name.setTag("payment.name");
            Spinner kind=Ui.spinner(a,box,"Payment type",List.of("Expense","Transfer / debt payment"));kind.setSelection(old!=null&&old.optString("payment_kind").equals("transfer")?1:0);
            Spinner account=Ui.spinner(a,box,"From account",Ui.labels(accounts));select(account,accounts,old==null?"":old.optString("account"));
            EditText amount=Ui.field(a,box,"Amount",old==null?"":old.optString("amount"),true);amount.setTag("payment.amount");
            List<String> names=new ArrayList<>(List.of("Uncategorized"));names.addAll(Ui.labels(categories));Spinner category=Ui.spinner(a,box,"Expense category",names);if(old!=null)for(int i=0;i<categories.size();i++)if(categories.get(i).optString("id").equals(old.optString("category")))category.setSelection(i+1);
            Spinner target=Ui.spinner(a,box,"To account / debt counterparty",Ui.labels(targets));select(target,targets,old==null?"":old.optString("to"));
            EditText received=Ui.field(a,box,"Received amount (destination currency)",old==null?"":old.optString("received_amount"),true);
            LocalDate[] start={old==null?LocalDate.now():LocalDate.parse(old.optString("start"))};Button date=Ui.button(a,start[0].toString(),()->{});date.setOnClickListener(v->new DatePickerDialog(a,(picker,y,m,d)->{start[0]=LocalDate.of(y,m+1,d);date.setText(start[0].toString());},start[0].getYear(),start[0].getMonthValue()-1,start[0].getDayOfMonth()).show());box.addView(Ui.text(a,"First payment date",12,Ui.MUTED));box.addView(date);
            Spinner frequency=Ui.spinner(a,box,"Repeat",Payments.FREQUENCIES);frequency.setSelection(old==null?2:Payments.FREQUENCIES.indexOf(old.optString("frequency")));
            EditText days=Ui.field(a,box,"Remind days before (0–30)",old==null?"1":old.optString("remind_days","1"),true),note=Ui.field(a,box,"Comment",old==null?"":old.optString("note"),false);
            CheckBox archived=new CheckBox(a);archived.setText(Strings.t(a,"Archived"));archived.setChecked(old!=null&&old.optBoolean("archived"));box.addView(archived);
            box.addView(Ui.text(a,"Changing the start date or repeat interval changes future calendar dates. Existing confirmed operations stay unchanged.",12,Ui.MUTED));
            AlertDialog dialog=new LocalizedDialog(a).setTitle(old==null?"Add planned payment":"Edit plan").setView(Ui.scroll(a,box)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{try{
                JSONObject source=accounts.get(account.getSelectedItemPosition());boolean transfer=kind.getSelectedItemPosition()==1;String currency=source.optString("currency");String value=Money.input(Ui.value(amount).replace(',','.'),currency);
                JSONObject p=Ledger.obj("id",old==null?UUID.randomUUID().toString():old.optString("id"),"entity","payment_plan","name",Ui.value(name),"account",source.optString("id"),"currency",currency,"amount",value,"payment_kind",transfer?"transfer":"expense","category",category.getSelectedItemPosition()==0?"":categories.get(category.getSelectedItemPosition()-1).optString("id"),"start",start[0].toString(),"frequency",Payments.FREQUENCIES.get(frequency.getSelectedItemPosition()),"remind_days",Integer.parseInt(Ui.value(days)),"note",Ui.value(note),"archived",archived.isChecked());
                if(transfer){JSONObject destination=targets.get(target.getSelectedItemPosition());if(destination.optString("id").equals(source.optString("id")))throw new IllegalArgumentException("Choose two different accounts.");String incoming=source.optString("currency").equals(destination.optString("currency"))&&Ui.value(received).isEmpty()?value:Money.input(Ui.value(received).replace(',','.'),destination.optString("currency"));if(Money.decimal(incoming).signum()<=0)throw new IllegalArgumentException("Received amount must be positive.");p.put("to",destination.optString("id"));p.put("received_amount",incoming);}
                Payments.validate(p);if(old!=null)p.put("_head",old.optString("_head"));dialog.getButton(-1).setEnabled(false);mutate(a,()->store.edit(p),()->{dialog.dismiss();changed.run();},()->dialog.getButton(-1).setEnabled(true));
            }catch(Exception e){amount.setError(Strings.t(a,e.getMessage()));}}));dialog.show();
        }catch(Exception e){Ui.message(a,"Payment",e.getMessage());}
    }
    private static void select(Spinner spinner,List<JSONObject> values,String id){for(int i=0;i<values.size();i++)if(values.get(i).optString("id").equals(id))spinner.setSelection(i);}
    private static void reminders(Activity a){
        a.getSharedPreferences("payment-reminders",0).edit().putBoolean("enabled",true).apply();PaymentReminderWorker.schedule(a);
        if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},73);return;}
        new LocalizedDialog(a).setTitle("Payment reminders").setMessage("Reminders start at 09:00 local time on the selected day. Android battery settings can delay delivery. No payment is made automatically.").setPositiveButton("OK",null).setNeutralButton("Android notification settings",(d,w)->a.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,a.getPackageName()))).setNegativeButton("Disable reminders",(d,w)->a.getSharedPreferences("payment-reminders",0).edit().putBoolean("enabled",false).apply()).show();
    }
}
