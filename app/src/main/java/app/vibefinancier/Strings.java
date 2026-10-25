package app.vibefinancier;

import android.content.*;
import android.content.res.Configuration;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** English source messages with an explicit Russian catalog; persisted data is never translated. */
final class Strings {
    private static Map<String,String> translations;
    static boolean russian(Context c){return c.getSharedPreferences("language",Context.MODE_PRIVATE).getString("locale","en").equals("ru");}
    static void set(Context c,String language){if(!Set.of("en","ru").contains(language))throw new IllegalArgumentException("Unknown language");c.getSharedPreferences("language",Context.MODE_PRIVATE).edit().putString("locale",language).apply();}
    static Locale locale(Context c){return russian(c)?Locale.forLanguageTag("ru"):Locale.ENGLISH;}
    static Context context(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(locale(c));return c.createConfigurationContext(config);}
    static synchronized String t(Context c,String text){
        if(text==null)return "";if(!russian(c))return text;
        if(translations==null){Map<String,String> values=new LinkedHashMap<>();try(java.io.InputStream in=c.getAssets().open("localization/ru.json")){JSONObject catalog=new JSONObject(new String(Streams.read(in,300_000),StandardCharsets.UTF_8));Iterator<String> keys=catalog.keys();List<String> sorted=new ArrayList<>();keys.forEachRemaining(sorted::add);sorted.sort((a,b)->Integer.compare(b.length(),a.length()));for(String key:sorted)values.put(key,catalog.getString(key));}catch(Exception e){throw new IllegalStateException("Russian catalog unavailable",e);}translations=values;}
        if(translations.containsKey(text))return translations.get(text);if(text.endsWith(" ▾"))return t(c,text.substring(0,text.length()-2))+" ▾";
        if(text.contains("\n")){StringJoiner lines=new StringJoiner("\n");for(String line:text.split("\n",-1))lines.add(t(c,line));return lines.toString();}
        for(Map.Entry<String,String> e:translations.entrySet()){String key=e.getKey();if(key.endsWith(" ")&&text.startsWith(key))return e.getValue()+t(c,text.substring(key.length()));if(key.startsWith(" ")&&text.endsWith(key))return t(c,text.substring(0,text.length()-key.length()))+e.getValue();}
        return text;
    }
    static String[] items(Context c,String[] values){String[] result=values.clone();for(int i=0;i<result.length;i++)result[i]=t(c,result[i]);return result;}
}
