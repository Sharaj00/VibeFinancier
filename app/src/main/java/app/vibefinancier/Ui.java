package app.vibefinancier;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

public final class Ui {
    public static int INK=0xff222522,MUTED=0xff606660,GREEN=0xff146c60,BG=0xfff9f9f9,CARD=Color.WHITE,SURFACE=0xffeeeeee,DIVIDER=0xffdddddd;
    public static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n);}
    public static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));return l;}
    public static TextView text(Activity a,String t,int size,int color){return rawText(a,Strings.t(a,t),size,color);}
    public static TextView rawText(Activity a,String t,int size,int color){TextView v=new TextView(a);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,5),0,dp(a,5));return v;}
    public static TextView title(Activity a,String t){return text(a,t,25,INK);}
    public static Button button(Activity a,String text,Runnable run){Button b=new Button(a);b.setText(Strings.t(a,text));b.setTextColor(GREEN);b.setAllCaps(false);b.setMinHeight(dp(a,48));b.setOnClickListener(v->run.run());return b;}
    public static EditText field(Activity a,LinearLayout parent,String label,String value,boolean decimal){parent.addView(text(a,label,12,MUTED));EditText v=new EditText(a);v.setSingleLine(true);v.setText(value);v.setTextSize(16);if(decimal)v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    public static Spinner spinner(Activity a,LinearLayout parent,String label,List<String> names){parent.addView(text(a,label,12,MUTED));Spinner s=new Spinner(a);s.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,Strings.items(a,names.toArray(new String[0]))));parent.addView(s,new LinearLayout.LayoutParams(-1,dp(a,48)));return s;}
    public static LinearLayout card(Activity a,LinearLayout parent){LinearLayout c=column(a);GradientDrawable g=new GradientDrawable();g.setColor(CARD);g.setCornerRadius(dp(a,14));if(Appearance.mode(a).equals("oled"))g.setStroke(dp(a,1),DIVIDER);c.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,5),0,dp(a,7));parent.addView(c,p);return c;}
    public static void message(Activity a,String title,String message){
        String content=message==null?"Unknown error":message;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("https://docs\\.google\\.com/spreadsheets/d/[A-Za-z0-9_-]+(?:/edit)?").matcher(content);
        AlertDialog.Builder builder=new LocalizedDialog(a).setTitle(title).setMessage(content).setPositiveButton("OK",null);
        if(match.find()){
            String link=match.group();
            builder.setNegativeButton("Open table",(d,w)->{try{a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(link)));}catch(android.content.ActivityNotFoundException e){message(a,"Open table","No browser or Google Sheets app is installed. Use Copy link to open the table on another device.");}});
            builder.setNeutralButton("Copy link",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Google table",link));Toast.makeText(a,"Table link copied",Toast.LENGTH_SHORT).show();});
        }
        AlertDialog dialog=builder.show();TextView text=dialog.findViewById(android.R.id.message);
        if(text!=null){text.setTextIsSelectable(true);android.text.util.Linkify.addLinks(text,android.text.util.Linkify.WEB_URLS);text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());}
    }
    public static ScrollView scroll(Activity a,LinearLayout child){ScrollView s=new ScrollView(a);s.setFillViewport(false);s.addView(child);return s;}
    public static List<String> labels(List<JSONObject> values){List<String> n=new ArrayList<>();for(JSONObject p:values)n.add(p.optString("name",p.optString("id"))+(p.has("currency")?" · "+p.optString("currency"):""));return n;}
    public static String value(EditText e){return e.getText().toString().trim();}
}
