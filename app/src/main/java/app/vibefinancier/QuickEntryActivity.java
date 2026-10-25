package app.vibefinancier;

import android.app.Activity;
import android.os.Bundle;

public class QuickEntryActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(Strings.context(base));}
    @Override public void onCreate(Bundle b){Appearance.apply(this);setTheme(Appearance.light(this)?R.style.QuickTheme:Appearance.mode(this).equals("oled")?R.style.OledQuickTheme:R.style.DarkQuickTheme);super.onCreate(b);EntryForm.show(this,getIntent().getStringExtra("kind")==null?"expense":getIntent().getStringExtra("kind"),null,()->{FinanceWidget.refresh(this);finish();},this::finish);}
}
