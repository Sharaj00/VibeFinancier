package app.vibefinancier;

import android.app.*;
import android.view.*;
import android.widget.*;

/** Visual selection keeps icon and color choices visible before saving. */
public class IconPicker extends LinearLayout {
    public interface SvgHost{void chooseSvgIcon(IconPicker target);}
    private final Activity activity;private String icon,svg="";private int color;
    public IconPicker(Activity activity,String icon,int color){super(activity);this.activity=activity;this.icon=icon;if(icon.startsWith("svg:")){this.icon="custom";svg=icon.substring(4);}this.color=color;setOrientation(VERTICAL);setGravity(Gravity.CENTER_HORIZONTAL);render();}
    public String icon(){return icon;}public String svg(){return svg;}public int color(){return color;}
    public void customSvg(String text)throws Exception{svg=SvgIcons.validate(text);icon="custom";render();}
    private void render(){removeAllViews();CircleIcon preview=new CircleIcon(activity,svg.isEmpty()?icon:"svg:"+svg,color);preview.setContentDescription("Choose icon and color");preview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);preview.setOnClickListener(v->choose());addView(preview,new LayoutParams(Ui.dp(activity,88),Ui.dp(activity,88)));addView(Ui.button(activity,"Choose icon & color",this::choose));if(activity instanceof SvgHost host)addView(Ui.button(activity,"Load SVG file",()->host.chooseSvgIcon(this)));}
    private void choose(){
        java.util.List<CircleIcon> previews=new java.util.ArrayList<>();LinearLayout box=Ui.column(activity);box.addView(Ui.text(activity,"Color",14,Ui.INK));GridLayout palette=new GridLayout(activity);palette.setColumnCount(6);box.addView(palette);
        for(int candidate:IconCatalog.COLORS){CircleIcon tile=new CircleIcon(activity,"",candidate);tile.setContentDescription(String.format("Color #%06X",candidate&0xffffff));tile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);tile.setOnClickListener(v->{color=candidate;for(CircleIcon preview:previews)preview.setColor(candidate);render();});palette.addView(tile,new GridLayout.LayoutParams(new ViewGroup.LayoutParams(Ui.dp(activity,44),Ui.dp(activity,44))));}
        box.addView(Ui.text(activity,"Icon · tap to apply",14,Ui.INK));GridLayout grid=new GridLayout(activity);grid.setColumnCount(4);box.addView(grid);
        AlertDialog dialog=new LocalizedDialog(activity).setTitle("Icon & color").setView(Ui.scroll(activity,box)).setPositiveButton("Done",(d,w)->render()).create();
        for(String candidate:IconCatalog.ICONS){LinearLayout cell=new LinearLayout(activity);cell.setOrientation(VERTICAL);cell.setGravity(Gravity.CENTER);CircleIcon tile=new CircleIcon(activity,candidate,color);previews.add(tile);cell.addView(tile,new LayoutParams(Ui.dp(activity,51),Ui.dp(activity,51)));TextView name=Ui.text(activity,candidate.replace('_',' '),10,Ui.INK);name.setGravity(Gravity.CENTER);cell.addView(name);cell.setContentDescription(candidate.replace('_',' '));cell.setOnClickListener(v->{icon=candidate;svg="";render();dialog.dismiss();});grid.addView(cell,new GridLayout.LayoutParams(new ViewGroup.LayoutParams(Ui.dp(activity,72),Ui.dp(activity,83))));}
        dialog.show();
    }
}
