package app.vibefinancier;

import android.app.*;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.os.*;
import android.text.*;
import android.text.method.NumberKeyListener;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.function.Consumer;

/** Transparent click dispatcher; only the small popup is visible over the launcher. */
public class WidgetDraftActivity extends Activity {
    private Store store;private int widget;private boolean busy,inputActive,imeSeen;private EditText editor;private AlertDialog popup;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(Strings.context(base));}
    @Override public void onCreate(Bundle state){
        Appearance.apply(this);setTheme(R.style.WidgetOverlayTheme);super.onCreate(state);
        Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);window.setGravity(Gravity.TOP|Gravity.START);window.setLayout(1,1);
        store=new Store(this);dispatch(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);dispatch(intent);}
    private void dispatch(Intent intent){
        if(busy)return;int next=intent.getIntExtra("widget",0);String part=intent.getStringExtra("part");
        try{
            if(inputActive){if(!completeInput())return;smallWindow();}
            closePopup();widget=next;
            if("save".equals(part)){busy=true;popup=WidgetPopup.saving(this);int target=widget;android.content.Context context=getApplicationContext();new Thread(()->{try{try(Store writer=new Store(context)){WidgetDraft.commit(writer,target);}FinanceWidget.refresh(context);SyncWorker.enqueue(context);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;closePopup();WidgetPopup.saved(this,this::finish);});}catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;closePopup();error(e);});}},"widget-save").start();return;}
            if("source".equals(part))select(true);
            else if("destination".equals(part)||"right".equals(part))select(false);
            else input(intent);
        }catch(Exception e){busy=false;error(e);}
    }
    private void smallWindow(){Window window=getWindow();window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);window.setGravity(Gravity.TOP|Gravity.START);window.setLayout(1,1);}
    private void input(Intent intent)throws Exception {
        JSONObject draft=WidgetDraft.load(store,widget);String expression=draft.optBoolean("editing")?draft.optString("expression"):draft.optString("amount");
        Window window=getWindow();window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);editor=new EditText(this);editor.setTag("widget.expression");editor.setContentDescription("Widget amount expression");editor.setSingleLine(true);editor.setBackgroundColor(android.graphics.Color.TRANSPARENT);editor.setTextColor(android.graphics.Color.TRANSPARENT);editor.setCursorVisible(false);editor.setSelectAllOnFocus(false);
        editor.setKeyListener(new NumberKeyListener(){@Override protected char[] getAcceptedChars(){return "0123456789.,+-*/() ×÷−".toCharArray();}@Override public int getInputType(){return InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;}});
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});editor.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);editor.setText(expression);editor.setSelection(editor.length());box.addView(editor,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));
        setContentView(box);Rect bounds=intent.getSourceBounds();int width=bounds==null?Ui.dp(this,180):Math.max(Ui.dp(this,120),bounds.width()),height=Ui.dp(this,44);int screenWidth=getResources().getDisplayMetrics().widthPixels,screenHeight=getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams params=window.getAttributes();params.width=Math.min(width,screenWidth-Ui.dp(this,16));params.height=height;params.gravity=Gravity.TOP|Gravity.START;params.x=bounds==null?(screenWidth-params.width)/2:Math.max(0,Math.min(bounds.left,screenWidth-params.width));params.y=bounds==null?(int)(screenHeight*.3):Math.max(Ui.dp(this,32),bounds.centerY()-Ui.dp(this,22));if(Build.VERSION.SDK_INT>=30)params.setFitInsetsTypes(0);window.setAttributes(params);
        inputActive=true;imeSeen=false;WidgetInput.expression(store,widget,expression);FinanceWidget.refreshAmount(this,widget,store);
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){try{WidgetInput.expression(store,widget,s.toString());FinanceWidget.refreshAmount(WidgetDraftActivity.this,widget,store);}catch(Exception e){Toast.makeText(WidgetDraftActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show();}}public void afterTextChanged(Editable e){}});
        editor.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE||event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN){if(inputActive&&completeInput())finish();return true;}return false;});
        if(Build.VERSION.SDK_INT>=30)box.setOnApplyWindowInsetsListener((v,insets)->{boolean visible=insets.isVisible(WindowInsets.Type.ime());if(visible)imeSeen=true;else if(imeSeen&&inputActive)handler.post(()->{if(inputActive&&completeInput())finish();});return insets;});
        editor.requestFocus();handler.postDelayed(()->{if(inputActive)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);},100);
    }
    private boolean completeInput(){try{WidgetInput.finish(store,widget);inputActive=false;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);FinanceWidget.refreshAmount(this,widget,store);return true;}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();return false;}}
    @Override public boolean onTouchEvent(MotionEvent event){if(inputActive&&event.getAction()==MotionEvent.ACTION_OUTSIDE){if(completeInput())finish();return true;}return super.onTouchEvent(event);}
    @Override public void onBackPressed(){if(inputActive){if(!completeInput())try{WidgetInput.cancel(store,widget);FinanceWidget.refreshAmount(this,widget,store);}catch(Exception ignored){}finish();}else super.onBackPressed();}
    private void error(Exception e){AlertDialog dialog=new LocalizedDialog(this).setMessage(e.getMessage()==null?"Unable to save the operation.":e.getMessage()).setPositiveButton("OK",(d,w)->finish()).create();dialog.setOnCancelListener(d->finish());WidgetPopup.show(this,dialog);}
    private void select(boolean source)throws Exception {
        closePopup();
        JSONObject draft=WidgetDraft.load(store,widget);
        popup=WidgetPopup.chooser(this,store,source,draft,item->{try{draft.put(source?"account":"destination",item.getString("id"));if(!source)draft.put("mode",item.optString("entity").equals("account")?"transfer":item.optString("direction"));WidgetDraft.update(store,widget,draft);FinanceWidget.refresh(this);finish();}catch(Exception e){error(e);}},this::finish,getIntent().getSourceBounds());
    }
    private void closePopup(){if(popup!=null){popup.dismiss();popup=null;}}
    static AlertDialog chooser(Activity activity,Store store,boolean source,JSONObject draft,Consumer<JSONObject> picked,Runnable cancel)throws Exception{return WidgetPopup.chooser(activity,store,source,draft,picked,cancel);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(store!=null)store.close();super.onDestroy();}
}
