package app.vibefinancier;

import org.json.JSONObject;
import java.util.*;

public final class CategoryChoices {
    private CategoryChoices(){}
    public static String root(String id,List<JSONObject> categories){Map<String,JSONObject> map=new HashMap<>();for(JSONObject p:categories)map.put(p.optString("id"),p);Set<String> seen=new HashSet<>();while(map.containsKey(id)){if(!seen.add(id))throw new IllegalArgumentException("Category parent cycle.");String parent=map.get(id).optString("parent");if(parent.isEmpty()||!map.containsKey(parent))break;id=parent;}return id;}
    public static String path(JSONObject category,List<JSONObject> categories){List<String> names=new ArrayList<>();Set<String> seen=new HashSet<>();JSONObject current=category;while(current!=null){String id=current.optString("id");if(!seen.add(id))throw new IllegalArgumentException("Category parent cycle.");names.add(current.optString("name"));String parent=current.optString("parent");current=null;for(JSONObject p:categories)if(p.optString("id").equals(parent)){current=p;break;}}Collections.reverse(names);return String.join(" › ",names);}
}
