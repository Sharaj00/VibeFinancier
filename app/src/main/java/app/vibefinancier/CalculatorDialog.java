package app.vibefinancier;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

public final class CalculatorDialog {
    private CalculatorDialog(){}
    public static void show(Activity activity,String initial,String currency,Consumer<String> applied){
        LinearLayout box=Ui.column(activity);EditText expression=Ui.field(activity,box,"Expression",initial,false);expression.setTag("calculator.expression");expression.setTextSize(27);expression.setShowSoftInputOnFocus(false);expression.setSelection(expression.length());
        TextView preview=Ui.text(activity,"Result in "+currency,16,Ui.MUTED);box.addView(preview);
        String[][] keys={{"C","(",")","⌫"},{"7","8","9","÷"},{"4","5","6","×"},{"1","2","3","−"},{"0",".","%","+"}};
        for(String[] row:keys){LinearLayout line=new LinearLayout(activity);for(String key:row)line.addView(Ui.button(activity,key,()->{if(key.equals("C"))expression.setText(Strings.t(activity,""));else if(key.equals("⌫")){int at=expression.getSelectionStart();if(at>0)expression.getText().delete(at-1,at);}else{int start=Math.max(0,expression.getSelectionStart()),end=Math.max(start,expression.getSelectionEnd());expression.getText().replace(start,end,key);}try{preview.setText(Calculator.evaluate(Ui.value(expression),Money.scale(currency))+" "+currency);}catch(Exception e){preview.setText(e.getMessage());}}),new LinearLayout.LayoutParams(0,Ui.dp(activity,49),1));box.addView(line);}
        AlertDialog dialog=new LocalizedDialog(activity).setTitle("Calculator").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{String result=Calculator.evaluate(Ui.value(expression),Money.scale(currency));Money.input(result,currency);if(Money.decimal(result).signum()<=0)throw new IllegalArgumentException("Amount must be greater than zero.");applied.accept(result);dialog.dismiss();}catch(Exception e){expression.setError(e.getMessage());}}));dialog.getWindow();dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
