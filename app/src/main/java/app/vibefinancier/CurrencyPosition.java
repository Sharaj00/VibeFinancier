package app.vibefinancier;

import org.json.*;
import java.math.*;
import java.util.*;

/** FIFO benchmark across non-debt accounts; internal same-currency transfers net to zero. */
final class CurrencyPosition {
    static final class Deposit {final String time,id;final BigDecimal units,rate;Deposit(String time,String id,BigDecimal units,BigDecimal rate){this.time=time;this.id=id;this.units=units;this.rate=rate;}}
    private static final class Lot {BigDecimal units;final BigDecimal rate;Lot(BigDecimal n,BigDecimal r){units=n;rate=r;}}
    final List<Deposit> deposits=new ArrayList<>();
    BigDecimal balance=BigDecimal.ZERO,knownUnits=BigDecimal.ZERO,cost=BigDecimal.ZERO;
    boolean partial;
    CurrencyPosition(List<JSONObject> values,String currency,String base)throws Exception {
        Set<String> accounts=new HashSet<>();for(JSONObject a:values)if(a.optString("entity").equals("account")&&a.optString("currency").equals(currency)&&!a.optString("account_type").equals("debt"))accounts.add(a.optString("id"));
        List<JSONObject> operations=new ArrayList<>();for(JSONObject p:values)if(p.optString("entity").equals("transaction"))operations.add(p);operations.sort(Comparator.comparing(p->p.optString("time")));
        ArrayDeque<Lot> lots=new ArrayDeque<>();BigDecimal shortfall=BigDecimal.ZERO;
        for(JSONObject p:operations){BigDecimal delta=BigDecimal.ZERO;JSONArray legs=p.optJSONArray("postings");if(legs!=null)for(int i=0;i<legs.length();i++){JSONObject l=legs.getJSONObject(i);if(accounts.contains(l.optString("account"))&&l.optString("currency").equals(currency))delta=delta.add(Money.decimal(l.getString("amount")));}balance=balance.add(delta);
            if(delta.signum()>0){BigDecimal rate=null;if(currency.equals(base))rate=BigDecimal.ONE;
                else if(p.optString("base_currency").equals(base)){
                    if(p.optString("currency").equals(currency)&&p.has("rate"))rate=Money.decimal(p.optString("rate"));
                    else if(p.optString("received_currency").equals(currency)&&Money.decimal(p.optString("received_amount","0")).signum()>0&&p.has("base_amount"))rate=Money.decimal(p.getString("base_amount")).add(Money.decimal(p.optString("fee_base","0"))).divide(Money.decimal(p.getString("received_amount")),18,RoundingMode.HALF_EVEN);
                }
                if(rate!=null&&rate.signum()<=0)rate=null;deposits.add(new Deposit(p.optString("time"),p.optString("id"),delta,rate));BigDecimal cover=delta.min(shortfall);shortfall=shortfall.subtract(cover);delta=delta.subtract(cover);if(delta.signum()>0)lots.add(new Lot(delta,rate));
            }else if(delta.signum()<0){BigDecimal remaining=delta.negate();while(remaining.signum()>0&&!lots.isEmpty()){Lot lot=lots.peek();BigDecimal take=remaining.min(lot.units);lot.units=lot.units.subtract(take);remaining=remaining.subtract(take);if(lot.units.signum()==0)lots.remove();}if(remaining.signum()>0){shortfall=shortfall.add(remaining);partial=true;}}
        }
        for(Lot lot:lots){if(lot.rate==null){partial=true;continue;}knownUnits=knownUnits.add(lot.units);cost=cost.add(lot.units.multiply(lot.rate));}if(balance.signum()<0)partial=true;
    }
    BigDecimal profit(BigDecimal quote){return knownUnits.multiply(quote).subtract(cost);}
}
