package app.vibefinancier;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

public final class Appearance {
    private Appearance(){}
    public static String mode(Context c){return c.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("theme","dark");}
    public static void set(Context c,String mode){if(!mode.equals("light")&&!mode.equals("dark")&&!mode.equals("oled"))throw new IllegalArgumentException("Unknown theme");c.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putString("theme",mode).apply();}
    public static boolean light(Context c){return mode(c).equals("light");}
    public static int background(Context c){return light(c)?Color.rgb(249,249,249):mode(c).equals("oled")?Color.BLACK:Color.rgb(18,18,18);}
    public static int surface(Context c){return light(c)?Color.rgb(238,239,238):mode(c).equals("oled")?Color.BLACK:Color.rgb(37,37,37);}
    public static void apply(Activity a){
        String mode=mode(a);a.setTheme(mode.equals("light")?R.style.AppTheme:mode.equals("oled")?R.style.OledTheme:R.style.DarkTheme);
        Ui.INK=light(a)?0xff222522:0xfff3f3f3;Ui.MUTED=light(a)?0xff606660:0xffb4b8b4;Ui.BG=background(a);Ui.CARD=light(a)?Color.WHITE:mode.equals("oled")?Color.BLACK:0xff222222;Ui.SURFACE=surface(a);Ui.DIVIDER=light(a)?0xffdddddd:0xff2b2b2b;Ui.GREEN=light(a)?0xff146c60:0xff8bc9b7;
        a.getWindow().setStatusBarColor(Ui.SURFACE);a.getWindow().setNavigationBarColor(Ui.BG);
        int flags=light(a)?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0;if(Build.VERSION.SDK_INT>=27&&light(a))flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
