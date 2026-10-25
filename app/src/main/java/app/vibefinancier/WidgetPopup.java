package app.vibefinancier;

import android.app.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Small translucent dialogs over the launcher, with bounded scrolling. */
final class WidgetPopup {
    private WidgetPopup(){}
    static AlertDialog chooser(Activity a,Store store,boolean source,JSONObject draft,Consumer<JSONObject> picked,Runnable cancel)throws Exception {
        return chooser(a,store,source,draft,picked,cancel,null);
    }
    static AlertDialog chooser(Activity a,Store store,boolean source,JSONObject draft,Consumer<JSONObject> picked,Runnable cancel,Rect anchor)throws Exception {
        LinearLayout panel=Ui.column(a);panel.setPadding(Ui.dp(a,18),Ui.dp(a,14),Ui.dp(a,18),Ui.dp(a,12));
        TextView title=Ui.text(a,source?"From account":"Expense category",20,Ui.INK);panel.addView(title);
        Button direction=Ui.button(a,draft.optString("mode").equals("income")?"Income · Change":"Expense · Change",()->{});direction.setBackgroundColor(Color.TRANSPARENT);direction.setVisibility(source?View.GONE:View.VISIBLE);panel.addView(direction);
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        int screenHeight=a.getResources().getDisplayMetrics().heightPixels;int below=anchor==null?screenHeight:screenHeight-anchor.bottom-Ui.dp(a,32),above=anchor==null?screenHeight:anchor.top-Ui.dp(a,40);boolean placeBelow=below>=above;
        int maxBody=Math.min((int)(screenHeight*.48f),Math.max(Ui.dp(a,72),Math.max(below,above)-Ui.dp(a,160)));
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(maxBody,MeasureSpec.AT_MOST));}};
        scroll.addView(body);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout navigation=new LinearLayout(a);panel.addView(navigation);
        Button back=Ui.button(a,"Back",()->{});navigation.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button use=Ui.button(a,"Select",()->{});use.setContentDescription("Select parent category");navigation.addView(use,new LinearLayout.LayoutParams(0,-2,1));
        Button close=Ui.button(a,"Cancel",()->{});navigation.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        for(Button button:new Button[]{back,use,close}){button.setBackgroundColor(Color.TRANSPARENT);button.setMinWidth(0);button.setPadding(0,0,0,0);}
        AlertDialog dialog=new LocalizedDialog(a).setView(panel).create();dialog.setView(panel,0,0,0,0);dialog.setOnCancelListener(d->cancel.run());
        List<JSONObject> entities=store.metadata();Deque<JSONObject> parents=new ArrayDeque<>();
        Runnable[] render={null};render[0]=()->{
            body.removeAllViews();JSONObject parent=parents.peekLast();String parentId=parent==null?"":parent.optString("id");
            direction.setVisibility(source||parent!=null?View.GONE:View.VISIBLE);
            title.setText(source?Strings.t(a,"From account"):parent==null?Strings.t(a,draft.optString("mode").equals("income")?"Income category":"Expense category / account"):parent.optString("name"));
            back.setVisibility(parent==null?View.GONE:View.VISIBLE);use.setVisibility(parent==null?View.GONE:View.VISIBLE);
            List<JSONObject> choices=WidgetInput.level(entities,source,draft.optString("mode"),parentId,draft.optString("account"));
            if(choices.isEmpty())body.addView(Ui.text(a,"No available choices.",14,Ui.MUTED));
            for(int start=0;start<choices.size();start+=3){LinearLayout row=new LinearLayout(a);body.addView(row);
                for(int column=0;column<3;column++){
                    LinearLayout tile=new LinearLayout(a);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(Ui.dp(a,3),Ui.dp(a,8),Ui.dp(a,3),Ui.dp(a,8));row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
                    int index=start+column;if(index>=choices.size())continue;JSONObject item=choices.get(index);String name=item.optString("name");
                    CircleIcon icon=new CircleIcon(a,IconCatalog.resolve(item),IconCatalog.color(item));tile.addView(icon,new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));
                    TextView label=Ui.rawText(a,name,13,Ui.INK);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(label);
                    tile.setContentDescription(name);tile.setOnClickListener(v->{
                        boolean children=item.optString("entity").equals("category")&&!WidgetInput.level(entities,false,draft.optString("mode"),item.optString("id"),draft.optString("account")).isEmpty();
                        if(children){parents.addLast(item);render[0].run();scroll.scrollTo(0,0);}else{dialog.dismiss();picked.accept(item);}
                    });
                }
            }
        };
        direction.setOnClickListener(v->{try{draft.put("mode",draft.optString("mode").equals("income")?"expense":"income");direction.setText(Strings.t(a,draft.optString("mode").equals("income")?"Income · Change":"Expense · Change"));parents.clear();render[0].run();}catch(Exception e){throw new IllegalStateException(e);}});
        back.setOnClickListener(v->{parents.pollLast();render[0].run();scroll.scrollTo(0,0);});
        use.setOnClickListener(v->{JSONObject item=parents.peekLast();if(item!=null){dialog.dismiss();picked.accept(item);}});
        close.setOnClickListener(v->{dialog.dismiss();cancel.run();});
        dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&!parents.isEmpty()){parents.pollLast();render[0].run();return true;}return false;});
        render[0].run();show(a,dialog);
        if(anchor!=null){
            dialog.setCanceledOnTouchOutside(false);Window window=dialog.getWindow();window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            window.getDecorView().setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_OUTSIDE){dialog.dismiss();cancel.run();return true;}return false;});
            WindowManager.LayoutParams params=window.getAttributes();params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;params.x=0;if(android.os.Build.VERSION.SDK_INT>=30)params.setFitInsetsTypes(0);
            window.getDecorView().measure(View.MeasureSpec.makeMeasureSpec(params.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.max(below,above),View.MeasureSpec.AT_MOST));
            params.y=placeBelow?anchor.bottom+Ui.dp(a,8):Math.max(Ui.dp(a,32),anchor.top-window.getDecorView().getMeasuredHeight()-Ui.dp(a,8));window.setAttributes(params);
        }
        return dialog;
    }
    static void show(Activity a,AlertDialog dialog){
        Window window=dialog.getWindow();if(window==null){dialog.show();return;}
        GradientDrawable background=new GradientDrawable();background.setColor(Appearance.light(a)?0xeffafafa:Appearance.mode(a).equals("oled")?0xe6000000:0xe6222426);background.setCornerRadius(Ui.dp(a,26));background.setStroke(Ui.dp(a,1),Appearance.light(a)?0x22ffffff:0x22ffffff);
        window.setBackgroundDrawable(background);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.16f);window.setWindowAnimations(R.style.WidgetPopupAnimation);
        dialog.show();
        window.setLayout(Math.min(Ui.dp(a,360),a.getResources().getDisplayMetrics().widthPixels-Ui.dp(a,32)),WindowManager.LayoutParams.WRAP_CONTENT);window.setGravity(Gravity.CENTER);
        window.getDecorView().setElevation(Ui.dp(a,14));
    }
    static void saved(Activity a,Runnable finished){
        LinearLayout box=Ui.column(a);box.setGravity(Gravity.CENTER);TextView check=Ui.text(a,"✓",48,0xff7de092);check.setGravity(Gravity.CENTER);box.addView(check);TextView label=Ui.text(a,"Saved",14,Ui.INK);label.setGravity(Gravity.CENTER);box.addView(label);
        AlertDialog dialog=new LocalizedDialog(a).setView(box).create();dialog.setOnDismissListener(d->finished.run());show(a,dialog);dialog.getWindow().setLayout(Ui.dp(a,132),Ui.dp(a,132));
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!a.isFinishing())dialog.dismiss();},900);
    }
    static AlertDialog saving(Activity a){LinearLayout box=Ui.column(a);box.setGravity(Gravity.CENTER);box.addView(new ProgressBar(a),new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,36)));AlertDialog dialog=new LocalizedDialog(a).setView(box).create();dialog.setCancelable(false);show(a,dialog);dialog.getWindow().setLayout(Ui.dp(a,100),Ui.dp(a,100));return dialog;}
}
