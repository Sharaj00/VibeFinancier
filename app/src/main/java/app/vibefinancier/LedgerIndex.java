package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Built off the UI thread once per resolved ledger revision. */
final class LedgerIndex {
    final Map<String,BigDecimal> balances;
    final Map<String,List<JSONObject>> operations=new HashMap<>(),categoryOperations=new HashMap<>();
    private final Map<String,AccountTimeline> timelines=new HashMap<>();
    LedgerIndex(List<JSONObject> values)throws Exception {
        balances=Ledger.balances(values);Map<String,String> parents=new HashMap<>();for(JSONObject p:values)if(p.optString("entity").equals("category"))parents.put(p.optString("id"),p.optString("parent"));
        for(JSONObject p:values)if(p.optString("entity").equals("transaction")){
            String category=p.optString("category");Set<String> seen=new HashSet<>();while(!category.isEmpty()&&seen.add(category)){categoryOperations.computeIfAbsent(category,k->new ArrayList<>()).add(p);category=parents.getOrDefault(category,"");}
            Set<String> accounts=new HashSet<>();JSONArray legs=p.optJSONArray("postings");if(legs!=null)for(int i=0;i<legs.length();i++)accounts.add(legs.getJSONObject(i).optString("account"));
            for(String id:accounts)operations.computeIfAbsent(id,k->new ArrayList<>()).add(p);
        }
        for(String id:balances.keySet())timelines.put(id,new AccountTimeline(id,operations.getOrDefault(id,List.of()),balances.get(id)));
    }
    AccountTimeline timeline(String id)throws Exception{return timelines.containsKey(id)?timelines.get(id):new AccountTimeline(id,List.of(),BigDecimal.ZERO);}
}
