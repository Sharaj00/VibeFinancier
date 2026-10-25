package app.vibefinancier;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

final class NavigationDrawer {
    interface Actions {void open(String destination);}
    static Dialog show(Activity a,String connection,String status,Actions actions){
        Dialog drawer=new Dialog(a);drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Ui.BG);
        LinearLayout header=Ui.column(a);header.setPadding(Ui.dp(a,20),Ui.dp(a,34),Ui.dp(a,20),Ui.dp(a,24));header.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Ui.SURFACE,Ui.BG}));header.addView(Ui.text(a,"VibeFinancier",25,Ui.INK));header.addView(Ui.text(a,"Personal finance",14,Ui.MUTED));header.addView(Ui.text(a,connection,12,Ui.MUTED));panel.addView(header);
        LinearLayout items=new LinearLayout(a);items.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(a);scroll.addView(items);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String[][] rows={{"Reports","chart-pie","#79A8CD"},{"Statistics","chart-no-axes-column","#AA96C9"},{"","",""},{"Transactions","list","#56BBC7"},{"","",""},{"Categories","tag","#79A8CD"},{"Currencies","coins","#E6C058"},{"Payments & subscriptions","calendar-days","#AA96C9"},{"","",""},{"Settings","settings","#E6C058"}};
        for(String[] item:rows){if(item[0].isEmpty()){View line=new View(a);line.setBackgroundColor(Ui.DIVIDER);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,Ui.dp(a,1));space.setMargins(0,Ui.dp(a,10),0,Ui.dp(a,10));items.addView(line,space);continue;}
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(a,18),0,Ui.dp(a,12),0);items.addView(row,new LinearLayout.LayoutParams(-1,Ui.dp(a,58)));View icon=new View(a){@Override protected void onDraw(Canvas canvas){canvas.save();canvas.scale(getWidth()/100f,getHeight()/100f);SvgIcons.draw(a,canvas,item[1],Color.parseColor(item[2]));canvas.restore();}};row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(a,42),Ui.dp(a,42)));TextView label=Ui.text(a,item[0],18,Ui.INK);label.setPadding(Ui.dp(a,16),0,0,0);row.addView(label);row.setContentDescription("Menu "+item[0]);row.setOnClickListener(v->{drawer.dismiss();actions.open(item[0]);});
        }
        TextView sync=Ui.text(a,status,12,Ui.INK);sync.setPadding(Ui.dp(a,20),Ui.dp(a,16),Ui.dp(a,20),Ui.dp(a,16));sync.setBackgroundColor(Ui.SURFACE);panel.addView(sync);drawer.setContentView(panel);Window window=drawer.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.5f);window.setWindowAnimations(R.style.DrawerAnimation);}drawer.show();if(window!=null){window.setLayout(Math.min(Ui.dp(a,360),(int)(a.getResources().getDisplayMetrics().widthPixels*.86f)),-1);window.setGravity(Gravity.START|Gravity.TOP);}return drawer;
    }
}
