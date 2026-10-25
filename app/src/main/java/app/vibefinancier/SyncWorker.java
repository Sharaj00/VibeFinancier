package app.vibefinancier;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    public SyncWorker(Context c,WorkerParameters p){super(c,p);}
    public static void enqueue(Context c) {
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueueUniqueWork("ledger-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,new OneTimeWorkRequest.Builder(SyncWorker.class).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("ledger-periodic",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(SyncWorker.class,30,TimeUnit.MINUTES).setConstraints(constraints).build());
    }
    @Override public Result doWork() {
        try(Store store=new Store(getApplicationContext())) {
            long last=Long.parseLong(store.get("rates_updated_at","0"));
            if(System.currentTimeMillis()-last>24L*60*60*1000&&!store.type("account",true).isEmpty()){
                try{Rates.update(store);store.set("rates_updated_at",Long.toString(System.currentTimeMillis()));store.set("rates_error","");}
                catch(Exception e){store.set("rates_error",e.getMessage()==null?"Rate provider unavailable":e.getMessage());}
            }
            if(store.get("sheet","").isEmpty())return Result.success();
            try {new Cloud(Cloud.backgroundToken(getApplicationContext())).sync(store);FinanceWidget.refresh(getApplicationContext());return store.pendingCount()>0?Result.retry():Result.success();}
            catch(Exception e){store.set("sync_error",e.getMessage()==null?"Synchronization failed":e.getMessage());return Result.retry();}
        }catch(Exception e){return Result.retry();}
    }
}
