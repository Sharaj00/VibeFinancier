package app.vibefinancier;

import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.work.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Inexact, battery-aware reminders survive reboots through WorkManager. Never posts transactions. */
public final class PaymentReminderWorker extends Worker {
    static final String CHANNEL="planned-payments";
    public PaymentReminderWorker(Context c,WorkerParameters p){super(c,p);}
    static void schedule(Context c){WorkManager manager=WorkManager.getInstance(c);manager.enqueueUniquePeriodicWork("planned-payments",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(PaymentReminderWorker.class,15,TimeUnit.MINUTES).build());manager.enqueueUniqueWork("planned-payments-now",ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(PaymentReminderWorker.class).build());}
    static List<String> due(List<JSONObject> values,LocalDateTime now){
        Set<String> done=Payments.completed(values);List<String> result=new ArrayList<>();
        for(JSONObject p:values)if(p.optString("entity").equals("payment_plan")&&!p.optBoolean("archived")){
            for(LocalDate d:Payments.dates(p,LocalDate.parse(p.optString("start")),now.toLocalDate().plusDays(p.optInt("remind_days")))){
                String id=Payments.occurrence(p,d);if(done.contains(id))continue;
                if(!now.isBefore(d.minusDays(p.optInt("remind_days")).atTime(9,0)))result.add(id);
                break;
            }
        }return result;
    }
    @Override public Result doWork(){
        Context c=getApplicationContext();if(!c.getSharedPreferences("payment-reminders",0).getBoolean("enabled",false))return Result.success();
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return Result.success();
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return Result.success();
        try(Store store=new Store(c)){
            List<String> ids=due(store.entities(false),LocalDateTime.now());List<String> fresh=new ArrayList<>();for(String id:ids)if(store.get("reminded."+id,"").isEmpty())fresh.add(id);if(fresh.isEmpty())return Result.success();
            NotificationChannel channel=new NotificationChannel(CHANNEL,Strings.t(c,"Payments & subscriptions"),NotificationManager.IMPORTANCE_DEFAULT);channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager.createNotificationChannel(channel);if(manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return Result.success();
            Intent intent=new Intent(c,MainActivity.class).putExtra("payments",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open=PendingIntent.getActivity(c,71,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_wallet).setContentTitle("VibeFinancier").setContentText(Strings.t(c,"Planned payments are ready for review.")).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
            manager.notify(71,notification);for(String id:fresh)store.set("reminded."+id,Instant.now().toString());return Result.success();
        }catch(Exception e){return Result.retry();}
    }
}
