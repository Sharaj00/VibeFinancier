package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

/** Original-currency daily movement is kept separate from saved historical base valuation. */
final class HistoryData {
    static final class Row {
        final JSONObject operation;final Map<String,BigDecimal> amounts=new TreeMap<>();
        Row(JSONObject p){operation=p;}
    }
    static final class Day {
        final String date;final List<Row> rows=new ArrayList<>();final Map<String,BigDecimal> amounts=new TreeMap<>();
        Day(String date){this.date=date;}
    }
    final Map<String,JSONObject> entities=new HashMap<>();final List<Day> days=new ArrayList<>();
    final String account,category,base;BigDecimal total=BigDecimal.ZERO;int missing,count;boolean hasIncome,hasExpense,hasFees;
    HistoryData(List<JSONObject> values,List<JSONObject> operations,String account,String category,String base)throws Exception {
        this.account=account;this.category=category;this.base=base;for(JSONObject p:values)if(!p.optString("entity").equals("transaction"))entities.put(p.optString("id"),p);
        List<JSONObject> sorted=new ArrayList<>(operations);sorted.sort((a,b)->b.optString("time").compareTo(a.optString("time")));Day day=null;
        for(JSONObject p:sorted){if(!p.optString("entity").equals("transaction")||p.optBoolean("deleted")||!category.isEmpty()&&!within(p.optString("category"),category))continue;
            if(!category.isEmpty()&&!Set.of("income","expense").contains(p.optString("kind")))continue;
            JSONArray legs=p.optJSONArray("postings");if(!account.isEmpty()){boolean found=false;if(legs!=null)for(int i=0;i<legs.length();i++)if(legs.getJSONObject(i).optString("account").equals(account))found=true;if(!found)continue;}
            String time=p.optString("time"),date=time.length()>=10?time.substring(0,10):"Undated";if(day==null||!day.date.equals(date)){day=new Day(date);days.add(day);}Row row=new Row(p);String kind=p.optString("kind");
            if(!account.isEmpty()){if(legs!=null)for(int i=0;i<legs.length();i++){JSONObject leg=legs.getJSONObject(i);if(leg.optString("account").equals(account))bump(row.amounts,leg.getString("currency"),Money.decimal(leg.getString("amount")));}}
            else if(!category.isEmpty()){
                BigDecimal fee=Money.decimal(p.optString("fee","0")),amount=Money.decimal(p.getString("amount"));if(kind.equals("expense"))amount=amount.add(fee).negate();else if(kind.equals("income"))amount=amount.subtract(fee);else continue;
                bump(row.amounts,p.getString("currency"),amount);
            }else if(legs!=null)for(int i=0;i<legs.length();i++){JSONObject leg=legs.getJSONObject(i);bump(row.amounts,leg.getString("currency"),Money.decimal(leg.getString("amount")));}
            for(var entry:row.amounts.entrySet())bump(day.amounts,entry.getKey(),entry.getValue());day.rows.add(row);count++;
            if(!category.isEmpty()){
                hasIncome|=kind.equals("income");hasExpense|=kind.equals("expense");hasFees|=Money.decimal(p.optString("fee","0")).signum()!=0;
                if(!p.optString("base_currency").equals(base)||!p.has("base_amount")||Money.decimal(p.optString("fee","0")).signum()!=0&&!p.has("fee_base")){missing++;continue;}
                BigDecimal amount=Money.decimal(p.getString("base_amount")),fee=Money.decimal(p.optString("fee_base","0"));total=total.add(kind.equals("expense")?amount.add(fee).negate():amount.subtract(fee));
            }
        }
        days.removeIf(d->d.rows.isEmpty());
    }
    boolean within(String id,String parent){Set<String> seen=new HashSet<>();while(!id.isEmpty()&&seen.add(id)){if(id.equals(parent))return true;JSONObject p=entities.get(id);id=p==null?"":p.optString("parent");}return false;}
    static void bump(Map<String,BigDecimal> values,String currency,BigDecimal amount){values.merge(currency,amount,BigDecimal::add);}
    JSONObject entity(String id){return entities.get(id);}
}
