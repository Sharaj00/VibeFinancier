package app.vibefinancier;

import android.app.AlertDialog;
import android.content.*;

final class LocalizedDialog extends AlertDialog.Builder {
    LocalizedDialog(Context c){super(c);}
    @Override public AlertDialog.Builder setTitle(CharSequence s){return super.setTitle(Strings.t(getContext(),s==null?null:s.toString()));}
    @Override public AlertDialog.Builder setMessage(CharSequence s){return super.setMessage(Strings.t(getContext(),s==null?null:s.toString()));}
    @Override public AlertDialog.Builder setPositiveButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setPositiveButton(Strings.t(getContext(),s.toString()),listener);}
    @Override public AlertDialog.Builder setNegativeButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setNegativeButton(Strings.t(getContext(),s.toString()),listener);}
    @Override public AlertDialog.Builder setNeutralButton(CharSequence s,DialogInterface.OnClickListener listener){return super.setNeutralButton(Strings.t(getContext(),s.toString()),listener);}
    @Override public AlertDialog.Builder setItems(CharSequence[] items,DialogInterface.OnClickListener listener){String[] labels=new String[items.length];for(int i=0;i<labels.length;i++)labels[i]=Strings.t(getContext(),items[i].toString());return super.setItems(labels,listener);}
    @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items,int checked,DialogInterface.OnClickListener listener){String[] labels=new String[items.length];for(int i=0;i<labels.length;i++)labels[i]=Strings.t(getContext(),items[i].toString());return super.setSingleChoiceItems(labels,checked,listener);}
}
