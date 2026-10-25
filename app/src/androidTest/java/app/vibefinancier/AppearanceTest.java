package app.vibefinancier;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    @Test public void nativeDashboardRendersEveryThemeAndPersistsSelection()throws Exception{
        var instrument=InstrumentationRegistry.getInstrumentation();Context context=instrument.getTargetContext();String original=Appearance.mode(context);
        try{
            for(String mode:List.of("light","dark","oled")){
                Appearance.set(context,mode);assertEquals(mode,Appearance.mode(context));
                Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));TestUi.ready(activity);
                Throwable[] error={null};
                instrument.runOnMainSync(()->{try{
                    List<JSONObject> entities=new ArrayList<>();Map<String,BigDecimal> balances=new HashMap<>(),amounts=new HashMap<>();
                    entities.add(Ledger.obj("id","salary","entity","category","name","Salary","direction","income","parent","","icon","coins","color",0xffffd74c));amounts.put("salary",new BigDecimal("75000"));
                    for(String[] a:new String[][]{{"cash","Cash","wallet","1250"},{"bank","Bank card","card","67000"},{"ton","TON wallet","crypto","1.125"}}){entities.add(Ledger.obj("id",a[0],"entity","account","name",a[1],"currency",a[0].equals("ton")?"TON":"RUB","account_type",a[0].equals("cash")?"cash":"bank_card","icon",a[2]));balances.put(a[0],new BigDecimal(a[3]));}
                    int index=0;for(String[] c:new String[][]{{"food","Food"},{"services","Services"},{"cart","Shopping"},{"home","Housing"},{"game","Games"},{"person","Other"}}){entities.add(Ledger.obj("id",c[0],"entity","category","name",c[1],"direction","expense","parent","","icon",c[0]));amounts.put(c[0],BigDecimal.valueOf(++index*250));}
                    entities.add(Ledger.obj("id","debt","entity","account","name","Counterparty","currency","RUB","account_type","debt","icon","debt"));balances.put("debt",new BigDecimal("3500"));
                    Dashboard view=new Dashboard(activity,entities,balances,amounts,"RUB",new BigDecimal("68250"),new BigDecimal("75000"),new BigDecimal("5250"),"2026-10-01 — 2026-10-23",new Dashboard.Actions(){public void category(JSONObject p){}public void account(JSONObject p){}public void edit(JSONObject p){}public void add(String group){}public void statistics(){}});
                    int width=activity.getResources().getDisplayMetrics().widthPixels;view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());assertTrue(view.getMeasuredHeight()>0);
                    view.getViewTreeObserver().dispatchOnPreDraw();Bitmap image=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));if(mode.equals("oled"))assertEquals(Color.BLACK,image.getPixel(width-2,image.getHeight()-2));
                    File dir=new File(context.getExternalFilesDir(null),"previews");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create preview directory");try(FileOutputStream out=new FileOutputStream(new File(dir,"dashboard-"+mode+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}try(FileOutputStream out=new FileOutputStream(new File(dir,"measurements-"+mode+".txt"))){out.write(measurements(view).getBytes(java.nio.charset.StandardCharsets.UTF_8));}image.recycle();activity.finish();
                }catch(Throwable t){error[0]=t;activity.finish();}});
                if(error[0]!=null)throw new AssertionError(error[0]);
            }
        }finally{Appearance.set(context,original);}
    }
    private static String measurements(View v){String result="";if(v instanceof android.widget.TextView t)result=t.getText()+" | "+v.getWidth()+"x"+v.getHeight()+" top="+v.getTop()+" baseline="+t.getBaseline()+" lines="+t.getLineCount()+"\n";if(v instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)result+=measurements(g.getChildAt(i));return result;}
}
