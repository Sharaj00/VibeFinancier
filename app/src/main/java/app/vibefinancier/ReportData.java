package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Exact report totals; only rendering converts proportions to floating point. */
final class ReportData {
    BigDecimal income=BigDecimal.ZERO,expense=BigDecimal.ZERO;int missing;
    final Map<String,BigDecimal> expenses=new LinkedHashMap<>(),incomes=new LinkedHashMap<>();
    final Map<String,JSONObject> categories=new LinkedHashMap<>();
    ReportData(List<JSONObject> entities,String base,String from,String to,String account,String category)throws Exception {
        if(LocalDate.parse(from).isAfter(LocalDate.parse(to)))throw new IllegalArgumentException("End date precedes start date.");
        for(JSONObject p:entities)if(p.optString("entity").equals("category"))categories.put(p.optString("id"),p);
        for(JSONObject p:entities){if(!p.optString("entity").equals("transaction"))continue;String time=p.optString("time");if(time.length()<10||time.substring(0,10).compareTo(from)<0||time.substring(0,10).compareTo(to)>0)continue;
            boolean matches=account.isEmpty();JSONArray legs=p.optJSONArray("postings");if(legs!=null)for(int i=0;i<legs.length();i++)if(legs.getJSONObject(i).optString("account").equals(account))matches=true;if(!matches||!category.isEmpty()&&!descendant(p.optString("category"),category))continue;
            String kind=p.optString("kind");boolean ordinary=kind.equals("income")||kind.equals("expense");BigDecimal fee=Money.decimal(p.optString("fee_base","0"));if(!ordinary&&fee.signum()==0)continue;
            if(!p.optString("base_currency").equals(base)||!p.has("base_amount")){missing++;continue;}
            if(ordinary){BigDecimal amount=Money.decimal(p.getString("base_amount"));String group=group(p.optString("category"),category);if(kind.equals("income")){income=income.add(amount);incomes.merge(group,amount,BigDecimal::add);}else{expense=expense.add(amount);expenses.merge(group,amount,BigDecimal::add);}}
            if(fee.signum()!=0&&(account.isEmpty()||p.optString("account").equals(account))){expense=expense.add(fee);expenses.merge("__fees__",fee,BigDecimal::add);}
        }
    }
    private boolean descendant(String id,String parent){Set<String> seen=new HashSet<>();while(!id.isEmpty()&&seen.add(id)){if(id.equals(parent))return true;id=categories.containsKey(id)?categories.get(id).optString("parent"):"";}return false;}
    private String group(String id,String filter){Set<String> seen=new HashSet<>();while(categories.containsKey(id)){if(!seen.add(id))throw new IllegalArgumentException("Category parent cycle.");String parent=categories.get(id).optString("parent");if(parent.isEmpty()||parent.equals(filter)||!categories.containsKey(parent)||id.equals(filter))break;id=parent;}return id;}
    static String[] shift(String from,String to,String period,int direction){LocalDate start=LocalDate.parse(from),end=LocalDate.parse(to);return switch(period){case "Month"->{LocalDate s=start.withDayOfMonth(1).plusMonths(direction);yield new String[]{s.toString(),s.withDayOfMonth(s.lengthOfMonth()).toString()};}case "Year"->{LocalDate s=start.withDayOfYear(1).plusYears(direction);yield new String[]{s.toString(),s.withDayOfYear(s.lengthOfYear()).toString()};}case "Week"->new String[]{start.plusWeeks(direction).toString(),end.plusWeeks(direction).toString()};default->{long days=java.time.temporal.ChronoUnit.DAYS.between(start,end)+1;yield new String[]{start.plusDays(days*direction).toString(),end.plusDays(days*direction).toString()};}};}
}
