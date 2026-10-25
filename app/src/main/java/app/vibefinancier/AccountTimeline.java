package app.vibefinancier;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

public final class AccountTimeline {
    public static final class Day {public final String date;public final BigDecimal closing;public BigDecimal movement=BigDecimal.ZERO;public final List<JSONObject> transactions=new ArrayList<>();Day(String date,BigDecimal closing){this.date=date;this.closing=closing;}}
    public final BigDecimal balance;public BigDecimal income=BigDecimal.ZERO,expense=BigDecimal.ZERO,toCounterparty=BigDecimal.ZERO,fromCounterparty=BigDecimal.ZERO;public final List<Day> days=new ArrayList<>();
    public AccountTimeline(String account,List<JSONObject> entities)throws Exception {
        this(account,entities,Ledger.balances(entities).getOrDefault(account,BigDecimal.ZERO));
    }
    AccountTimeline(String account,List<JSONObject> entities,BigDecimal current)throws Exception {
        balance=current;List<JSONObject> operations=new ArrayList<>();
        for(JSONObject p:entities)if(p.optString("entity").equals("transaction")){JSONArray legs=p.optJSONArray("postings");if(legs!=null)for(int i=0;i<legs.length();i++)if(legs.getJSONObject(i).optString("account").equals(account)){operations.add(p);break;}}
        operations.sort((a,b)->b.optString("time").compareTo(a.optString("time")));BigDecimal running=balance;Day day=null;
        for(JSONObject p:operations){String time=p.optString("time"),date=time.length()>=10?time.substring(0,10):"Undated";if(day==null||!day.date.equals(date)){day=new Day(date,running);days.add(day);}BigDecimal amount=movement(p,account);day.transactions.add(p);day.movement=day.movement.add(amount);running=running.subtract(amount);
            if(p.optString("kind").equals("income")){income=income.add(Money.decimal(p.optString("amount","0")));expense=expense.add(Money.decimal(p.optString("fee","0")));}if(p.optString("kind").equals("expense"))expense=expense.add(amount.negate().max(BigDecimal.ZERO));
            if(p.optString("kind").equals("transfer")||p.optString("kind").equals("debt")){if(amount.signum()>0)toCounterparty=toCounterparty.add(amount);else fromCounterparty=fromCounterparty.add(amount.abs());}
            if((p.optString("kind").equals("transfer")||p.optString("kind").equals("debt"))&&p.optString("account").equals(account))expense=expense.add(Money.decimal(p.optString("fee","0")));
        }
    }
    public static BigDecimal movement(JSONObject transaction,String account)throws Exception {BigDecimal sum=BigDecimal.ZERO;JSONArray legs=transaction.optJSONArray("postings");if(legs!=null)for(int i=0;i<legs.length();i++){JSONObject leg=legs.getJSONObject(i);if(leg.optString("account").equals(account))sum=sum.add(Money.decimal(leg.getString("amount")));}return sum;}
}
