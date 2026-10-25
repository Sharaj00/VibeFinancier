package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Device-side exact calculations. Sheet summaries are disposable snapshots. */
public final class LedgerReport {
    private LedgerReport(){}
    public static JSONArray calculate(List<JSONObject> events,String base,String from,String to,String accountFilter,String categoryFilter)throws Exception {
        LocalDate.parse(from);LocalDate.parse(to);if(from.compareTo(to)>0)throw new IllegalArgumentException("End date precedes start date.");
        List<JSONObject> entities=Ledger.entities(events,false);Map<String,JSONObject> accounts=new LinkedHashMap<>(),categories=new HashMap<>(),rates=new HashMap<>();
        Map<String,BigDecimal> balances=Ledger.balances(entities),groups=new LinkedHashMap<>(),months=new TreeMap<>();
        List<String> warnings=new ArrayList<>(),missingQuotes=new ArrayList<>();BigDecimal income=BigDecimal.ZERO,expense=BigDecimal.ZERO,assets=BigDecimal.ZERO;
        for(JSONObject p:entities){String kind=p.optString("entity"),id=p.optString("id");if(kind.equals("account"))accounts.put(id,p);if(kind.equals("category"))categories.put(id,p);if(kind.equals("rate")&&p.optString("base").equals(base))rates.put(p.optString("currency"),p);}
        for(JSONObject p:entities)if(p.optString("entity").equals("transaction")){
            JSONArray legs=p.optJSONArray("postings");boolean inAccount=accountFilter.isEmpty();
            if(legs!=null)for(int i=0;i<legs.length();i++){JSONObject leg=legs.getJSONObject(i);String id=leg.getString("account");if(id.equals(accountFilter))inAccount=true;if(!accounts.containsKey(id))warnings.add("Missing account: "+id);else if(!leg.getString("currency").equals(accounts.get(id).getString("currency")))throw new JSONException("Posting currency mismatch");}
            if(p.optJSONArray("unresolved")!=null&&p.getJSONArray("unresolved").length()>0)warnings.add("Unresolved source references: "+p.optString("id"));
            String time=p.optString("time");if(time.length()<10||time.substring(0,10).compareTo(from)<0||time.substring(0,10).compareTo(to)>0||!inAccount)continue;
            if(!categoryFilter.isEmpty()&&!inCategory(p.optString("category"),categoryFilter,categories))continue;
            String kind=p.optString("kind");if(!kind.equals("income")&&!kind.equals("expense")&&!p.has("fee_base"))continue;
            if(!p.optString("base_currency").equals(base)||!p.has("base_amount")){warnings.add("Historical rate unavailable: "+p.optString("id"));continue;}
            BigDecimal amount=Money.decimal(p.getString("base_amount"));
            if(kind.equals("income"))income=income.add(amount);if(kind.equals("expense"))expense=expense.add(amount);
            if(kind.equals("income")||kind.equals("expense")){
                String category=p.optString("category");Set<String> seen=new HashSet<>();
                do{if(!seen.add(category))throw new JSONException("Category parent cycle");bump(groups,kind+"\t"+category,amount);category=categories.containsKey(category)?categories.get(category).optString("parent"):"";}while(!category.isEmpty());
                bump(months,time.substring(0,7),kind.equals("expense")?amount.negate():amount);
            }
            if(p.has("fee_base")&&(accountFilter.isEmpty()||p.optString("account").equals(accountFilter))){BigDecimal fee=Money.decimal(p.getString("fee_base"));expense=expense.add(fee);bump(months,time.substring(0,7),fee.negate());}
        }
        JSONArray result=new JSONArray().put(new JSONArray(List.of("section","id","label","currency","exact_value","detail")));
        for(var entry:accounts.entrySet()){
            String id=entry.getKey();JSONObject p=entry.getValue();String currency=p.getString("currency");BigDecimal amount=balances.get(id);boolean debt=p.optString("account_type").equals("debt");
            row(result,"balance",id,p.optString("name"),currency,Money.plain(amount),debt?"+ owed to you / - you owe":"");
            if(!debt&&!p.optBoolean("exclude_from_total")){if(currency.equals(base))assets=assets.add(amount);else if(rates.containsKey(currency))assets=assets.add(amount.multiply(Money.decimal(rates.get(currency).getString("rate"))));else if(amount.signum()!=0)missingQuotes.add(currency);}
        }
        row(result,"assets","","Current asset valuation",base,Money.plain(assets),missingQuotes.isEmpty()?"Current quotes; debt accounts excluded":"PARTIAL: missing quotes for "+String.join(", ",missingQuotes));
        row(result,"income","","Income",base,Money.plain(income),from+" / "+to);row(result,"expense","","Expenses including fees",base,Money.plain(expense),from+" / "+to);row(result,"difference","","Income minus expenses",base,Money.plain(income.subtract(expense)),"Transfers, debt principal and openings excluded");
        for(var e:groups.entrySet()){String[] key=e.getKey().split("\t",-1);row(result,"category_"+key[0],key[1],categories.containsKey(key[1])?categories.get(key[1]).optString("name"):"Missing / uncategorized",base,Money.plain(e.getValue()),"Includes descendants; do not sum parent and child rows");}
        for(var e:months.entrySet())row(result,"month_net",e.getKey(),e.getKey(),base,Money.plain(e.getValue()),"Historical saved rates");
        for(var e:Ledger.heads(events).entrySet())if(e.getValue().size()>1)row(result,"conflict",e.getKey(),"Resolve in app","","","Excluded until resolved");
        if(!warnings.isEmpty())row(result,"warning","","Review import / exchange rates","",Integer.toString(warnings.size()),String.join("; ",warnings.subList(0,Math.min(10,warnings.size()))));
        return result;
    }
    private static boolean inCategory(String id,String parent,Map<String,JSONObject> categories)throws Exception {
        Set<String> seen=new HashSet<>();while(!id.isEmpty()){if(!seen.add(id))throw new JSONException("Category parent cycle");if(id.equals(parent))return true;id=categories.containsKey(id)?categories.get(id).optString("parent"):"";}return false;
    }
    private static void bump(Map<String,BigDecimal> map,String key,BigDecimal amount){map.merge(key,amount,BigDecimal::add);}
    private static void row(JSONArray rows,String section,String id,String label,String currency,String value,String detail){rows.put(new JSONArray(List.of(section,id,label,currency,value,detail)));}
}
