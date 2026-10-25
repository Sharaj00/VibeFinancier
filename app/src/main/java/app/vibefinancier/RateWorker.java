package app.vibefinancier;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Independent refresh also works for local-only ledgers without Google sign-in. */
public final class RateWorker extends Worker {
    public RateWorker(Context c,WorkerParameters parameters){super(c,parameters);}
    static void schedule(Context c){Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();WorkManager manager=WorkManager.getInstance(c);manager.enqueueUniquePeriodicWork("currency-quotes",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(RateWorker.class,6,TimeUnit.HOURS).setConstraints(constraints).build());manager.enqueueUniqueWork("currency-quotes-now",ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(RateWorker.class).setConstraints(constraints).build());}
    @Override public Result doWork(){try(Store store=new Store(getApplicationContext())){if(store.type("account",false).isEmpty())return Result.success();if(System.currentTimeMillis()-Long.parseLong(store.get("rates_updated_at","0"))<30L*60*1000)return Result.success();try{Rates.update(store);FinanceWidget.refresh(getApplicationContext());return Result.success();}catch(Exception e){store.set("rates_error",e.getMessage()==null?"Rate provider unavailable":e.getMessage());return Result.success();}}catch(Exception e){return Result.retry();}}
}
