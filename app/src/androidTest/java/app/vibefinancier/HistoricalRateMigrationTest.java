package app.vibefinancier;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Explicit maintenance action for the user-authorized historical valuation migration. */
public class HistoricalRateMigrationTest {
    private static void emit(String text){android.os.Bundle output=new android.os.Bundle();output.putString("stream","\n"+text+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(0,output);}
    @Test public void requestedHistoricalMigration()throws Exception {
        String mode=InstrumentationRegistry.getArguments().getString("rate_migration","");Assume.assumeTrue(mode.equals("preview")||mode.equals("apply")||mode.equals("report"));var context=InstrumentationRegistry.getInstrumentation().getTargetContext();File preview=new File(context.getFilesDir(),"historical-rate-review.json");
        try(Store store=new Store(context)){
            List<JSONObject> before=store.entities(false);Map<String,java.math.BigDecimal> balances=Ledger.balances(before);
            if(mode.equals("preview")){RateRepair plan=RateRepair.prepare(before);JSONObject data=Ledger.obj("format","vibefinancier-rate-review","replacements",new JSONArray(plan.replacements),"skipped",new JSONArray(plan.skipped));String text=data.toString();JSONObject envelope=Ledger.obj("content",text,"sha256",Ledger.hash(text));try(FileOutputStream out=new FileOutputStream(preview)){out.write(envelope.toString().getBytes(StandardCharsets.UTF_8));}emit("HISTORICAL PREVIEW\n"+plan.report());return;}
            JSONObject envelope=new JSONObject(new String(java.nio.file.Files.readAllBytes(preview.toPath()),StandardCharsets.UTF_8));String content=envelope.getString("content");assertEquals(envelope.getString("sha256"),Ledger.hash(content));JSONObject data=new JSONObject(content);assertEquals("vibefinancier-rate-review",data.getString("format"));RateRepair plan=new RateRepair(List.of(),(cur,base,date)->{throw new IllegalStateException("Apply must use the reviewed plan without fetching new quotes.");});JSONArray list=data.getJSONArray("replacements");for(int i=0;i<list.length();i++)plan.replacements.add(list.getJSONObject(i));JSONArray skipped=data.getJSONArray("skipped");for(int i=0;i<skipped.length();i++)plan.skipped.add(skipped.getString(i));
            if(mode.equals("report")){emit(plan.report());return;}
            if(plan.replacements.isEmpty()){store.set("rate_repair_report",plan.report());emit("No verified operations to change.\n"+plan.report());return;}
            File backup=new File(context.getFilesDir(),"before-rate-repair-"+System.currentTimeMillis()+".vfbackup.json");String saved=store.backup().toString();try(FileOutputStream out=new FileOutputStream(backup)){out.write(saved.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}JSONObject checked=new JSONObject(new String(java.nio.file.Files.readAllBytes(backup.toPath()),StandardCharsets.UTF_8));assertEquals(checked.getString("sha256"),Ledger.hash(checked.getString("content")));store.set("rate_repair_backup",backup.getAbsolutePath());plan.commit(store);
            List<JSONObject> after=store.entities(false);assertEquals("Balances must stay unchanged",balances,Ledger.balances(after));assertEquals(before.stream().filter(p->p.optString("entity").equals("transaction")).count(),after.stream().filter(p->p.optString("entity").equals("transaction")).count());Map<String,JSONObject> old=new HashMap<>();for(JSONObject p:before)old.put(p.optString("id"),p);for(JSONObject p:after)if(p.optString("entity").equals("transaction")){JSONObject previous=old.get(p.optString("id"));for(String field:List.of("amount","currency","received_amount","received_currency","execution_rate","time","postings"))assertEquals("Migration must preserve "+field,previous.optString(field),p.optString(field));}
            FinanceWidget.refresh(context);SyncWorker.enqueue(context);emit("APPLIED "+plan.replacements.size()+" historical valuation revisions; "+plan.skipped.size()+" skipped. Balances, postings and transaction count unchanged. Private backup verified.\n"+plan.report());
        }
    }
}
