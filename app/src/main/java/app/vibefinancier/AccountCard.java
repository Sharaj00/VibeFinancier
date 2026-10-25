package app.vibefinancier;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

public final class AccountCard {
    private AccountCard(){}
    public static View create(Activity activity,JSONObject account,AccountTimeline timeline,Runnable edit){
        return create(activity,account,timeline,edit,account.optString("currency"),java.math.BigDecimal.ONE);
    }
    static View create(Activity activity,JSONObject account,AccountTimeline timeline,Runnable edit,String currency,java.math.BigDecimal factor){
        LinearLayout card=Ui.column(activity);int color=IconCatalog.color(account),ink=0xff171b18;GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,lighten(color)});background.setCornerRadius(Ui.dp(activity,16));card.setBackground(background);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(new CircleIcon(activity,IconCatalog.resolve(account),color),new LinearLayout.LayoutParams(Ui.dp(activity,52),Ui.dp(activity,52)));TextView title=Ui.rawText(activity,account.optString("name")+"\n"+account.optString("currency"),20,ink);title.setPadding(Ui.dp(activity,10),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button button=Ui.button(activity,"✎",edit);button.setTextColor(ink);button.setTextSize(25);button.setContentDescription(Strings.t(activity,"Edit account"));button.setBackgroundColor(Color.TRANSPARENT);button.setMinWidth(0);heading.addView(button,new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));card.addView(heading);
        card.addView(Ui.text(activity,account.optString("account_type").equals("debt")?"Debt balance":"Balance",13,ink));TextView balance=Ui.text(activity,format(timeline.balance,factor,currency),33,ink);balance.setSingleLine(true);balance.setAutoSizeTextTypeUniformWithConfiguration(18,33,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(balance);
        LinearLayout totals=new LinearLayout(activity);totals.addView(Ui.text(activity,(account.optString("account_type").equals("debt")?Strings.t(activity,"To counterparty"):Strings.t(activity,"Income"))+"\n"+format(account.optString("account_type").equals("debt")?timeline.toCounterparty:timeline.income,factor,currency),15,ink),new LinearLayout.LayoutParams(0,-2,1));totals.addView(Ui.text(activity,(account.optString("account_type").equals("debt")?Strings.t(activity,"From counterparty"):Strings.t(activity,"Expenses"))+"\n"+format(account.optString("account_type").equals("debt")?timeline.fromCounterparty:timeline.expense,factor,currency),15,ink),new LinearLayout.LayoutParams(0,-2,1));card.addView(totals);
        if(account.optBoolean("exclude_from_total"))card.addView(Ui.text(activity,"Excluded from total balance",12,ink));return card;
    }
    private static String format(java.math.BigDecimal amount,java.math.BigDecimal factor,String currency){return Dashboard.format(amount.multiply(factor).setScale(Money.scale(currency),java.math.RoundingMode.HALF_EVEN),currency);}
    private static int lighten(int color){return Color.rgb((Color.red(color)*3+255)/4,(Color.green(color)*3+255)/4,(Color.blue(color)*3+255)/4);}
}
