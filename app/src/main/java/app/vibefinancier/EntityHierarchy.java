package app.vibefinancier;

import org.json.*;
import java.util.*;

/** Metadata moves preserve IDs, postings and historical operation kinds. */
final class EntityHierarchy {
    static boolean descendant(String id,String ancestor,List<JSONObject> values){
        Map<String,JSONObject> index=new HashMap<>();for(JSONObject p:values)index.put(p.optString("id"),p);
        Set<String> seen=new HashSet<>();while(!id.isEmpty()&&seen.add(id)){if(id.equals(ancestor))return true;JSONObject p=index.get(id);id=p==null?"":p.optString("parent");}return false;
    }
    static List<JSONObject> move(JSONObject item,String parent,String group,List<JSONObject> values)throws Exception {
        String entity=item.getString("entity"),id=item.getString("id");
        if(!Set.of("account","category").contains(entity))throw new IllegalArgumentException("Only accounts and categories can be moved.");
        if(!parent.isEmpty()){
            JSONObject target=null;for(JSONObject p:values)if(p.optString("id").equals(parent))target=p;
            if(target==null||target.optBoolean("archived")||!target.optString("entity").equals(entity))throw new IllegalArgumentException("Choose an active parent of the same kind.");
            if(descendant(parent,id,values))throw new IllegalArgumentException("A category cannot be its own parent.");
            group=target.optString(entity.equals("account")?"account_type":"direction");
        }
        if(entity.equals("category")&&!Set.of("income","expense").contains(group)||entity.equals("account")&&!Set.of("cash","bank_card","savings","crypto","debt","other").contains(group))throw new IllegalArgumentException("Invalid category group.");
        List<JSONObject> changes=new ArrayList<>();
        for(JSONObject p:values)if(p.optString("entity").equals(entity)&&descendant(p.optString("id"),id,values)){
            JSONObject copy=new JSONObject(p.toString());copy.put(entity.equals("account")?"account_type":"direction",group);if(p.optString("id").equals(id))copy.put("parent",parent);
            String head=copy.optString("_head");copy.remove("_head");changes.add(Ledger.event(copy,head,UUID.randomUUID().toString()));
        }
        return changes;
    }
    static List<JSONObject> archive(JSONObject item,List<JSONObject> values)throws Exception {
        List<JSONObject> changes=new ArrayList<>();for(JSONObject p:values)if(p.optString("entity").equals(item.optString("entity"))&&descendant(p.optString("id"),item.optString("id"),values)){
            JSONObject copy=new JSONObject(p.toString());copy.put("archived",true);String head=copy.optString("_head");copy.remove("_head");changes.add(Ledger.event(copy,head,UUID.randomUUID().toString()));
        }return changes;
    }
}
