package app.vibefinancier;

import org.json.*;
import java.util.*;

public final class IconCatalog {
    private IconCatalog(){}
    public static final List<String> ICONS=List.of("coins","wallet","card","bank","check","letter_t","savings","crypto","food","cart","home","services","game","person","debt","percent","car","bus","plane","coffee","gift","health","book","phone","pet","work");
    public static final int[] COLORS={0xffffd34d,0xff78b85b,0xffcb7157,0xffa79aaf,0xffffed82,0xffed6067,0xff76bfc4,0xffffa34d,0xff6ea7e6,0xffb1a7ef,0xffed9cc0,0xffb9bdc2};
    public static String resolve(JSONObject entity){
        String svg=entity.optString("icon_svg");if(!svg.isEmpty())return "svg:"+svg;
        String explicit=entity.optString("icon");if(!explicit.isEmpty())return explicit;
        JSONObject source=entity.optJSONObject("source");JSONArray raw=source==null?null:source.optJSONArray("raw");
        if(raw!=null&&source.optString("section").equals("category")){
            String mapped=switch(raw.optString(2)){case "i17"->"services";case "h6"->"home";case "e19"->"food";case "l3"->"game";case "e11"->"cart";case "b16"->"coins";case "b24"->"check";case "b27"->"letter_t";case "b19"->"savings";case "i10"->"person";case "b9"->"percent";default->"";};if(!mapped.isEmpty())return mapped;
        }
        return entity.optString("entity").equals("account")?entity.optString("account_type").equals("debt")?"debt":"wallet":entity.optString("direction").equals("income")?"coins":"cart";
    }
    public static int color(JSONObject entity){if(entity.has("color"))return entity.optInt("color",COLORS[0]);JSONObject source=entity.optJSONObject("source");JSONArray raw=source==null?null:source.optJSONArray("raw");if(raw!=null&&source.optString("section").equals("category"))try{return Integer.parseInt(raw.getString(7));}catch(Exception ignored){}return COLORS[0];}
}
