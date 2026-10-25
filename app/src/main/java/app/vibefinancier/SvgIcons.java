package app.vibefinancier;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import com.caverock.androidsvg.SVG;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Embedded vector icons have no dependency on a document URI after import. */
public final class SvgIcons {
    public static final int MAX_BYTES=16000;
    private static final LruCache<String,Picture> CACHE=new LruCache<>(80);
    private SvgIcons(){}
    public static String asset(String key){return switch(key){case "card","bank_card"->"credit-card";case "bank"->"landmark";case "check"->"badge-check";case "letter_t","work"->"briefcase-business";case "savings"->"piggy-bank";case "crypto","diamond"->"gem";case "food"->"utensils";case "cart","shopping"->"shopping-cart";case "home"->"house";case "services"->"handshake";case "game"->"gamepad-2";case "person"->"user-round";case "debt"->"hand-coins";case "health"->"heart-pulse";case "book"->"book-open";case "phone"->"smartphone";case "pet"->"paw-print";case "filter"->"list-filter";case "back"->"arrow-left";case "cash","purse"->"wallet";default->key;};}
    public static String validate(String text)throws Exception {
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("SVG must be 16 KB or smaller.");
        String lower=text.toLowerCase(Locale.ROOT);
        if(!lower.contains("<svg")||lower.contains("<!doctype")||lower.contains("<!entity")||lower.contains("<script")||lower.contains("<foreignobject")||lower.contains("<image")||lower.contains("@import")||lower.matches("(?s).*\\son[a-z]+\\s*=.*"))throw new IllegalArgumentException("Choose a self-contained vector SVG without scripts, external resources or embedded images.");
        java.util.regex.Matcher href=java.util.regex.Pattern.compile("(?:href)\\s*=\\s*(['\"])(.*?)\\1",java.util.regex.Pattern.DOTALL).matcher(lower);while(href.find())if(!href.group(2).startsWith("#"))throw new IllegalArgumentException("SVG references must stay inside the file.");
        java.util.regex.Matcher urls=java.util.regex.Pattern.compile("url\\s*\\(([^)]*)\\)").matcher(lower);while(urls.find())if(!urls.group(1).trim().replace("\"","").replace("'","").startsWith("#"))throw new IllegalArgumentException("SVG references must stay inside the file.");
        SVG svg=SVG.getFromString(text);if(svg.getDocumentViewBox()==null&&(svg.getDocumentWidth()<=0||svg.getDocumentHeight()<=0))throw new IllegalArgumentException("SVG needs a viewBox or positive width and height.");
        picture(svg);return text;
    }
    private static Picture picture(SVG svg){if(svg.getDocumentViewBox()==null)svg.setDocumentViewBox(0,0,svg.getDocumentWidth(),svg.getDocumentHeight());svg.setDocumentWidth(64);svg.setDocumentHeight(64);return svg.renderToPicture(64,64);}
    public static boolean draw(Context context,Canvas canvas,String icon,int ink){
        if(icon.isEmpty())return true;
        try{
            String key=Ledger.hash(icon+":"+ink);Picture picture=CACHE.get(key);
            if(picture==null){String text;if(icon.startsWith("svg:"))text=validate(icon.substring(4));else try(var input=context.getAssets().open("lucide/"+asset(icon)+".svg")){text=new String(Streams.read(input,MAX_BYTES),StandardCharsets.UTF_8);}text=text.replaceAll("(?i)currentcolor",String.format(Locale.ROOT,"#%06x",ink&0xffffff));SVG svg=SVG.getFromString(text);picture=picture(svg);CACHE.put(key,picture);}
            canvas.drawPicture(picture,new RectF(20,20,80,80));return true;
        }catch(Exception e){return false;}
    }
}
