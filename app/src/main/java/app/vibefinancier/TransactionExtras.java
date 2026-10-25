package app.vibefinancier;

import org.json.*;
import java.math.*;

final class TransactionExtras {
    static void fee(JSONObject tx,String value)throws Exception {
        String currency=tx.getString("currency"),base=tx.getString("base_currency"),fee=Money.input(value.isBlank()?"0":value,currency);tx.put("fee",fee);tx.put("fee_base",Money.convert(fee,tx.getString("rate"),Money.scale(base)));
        BigDecimal amount=Money.decimal(tx.getString("amount")),cost=Money.decimal(fee);String kind=tx.optString("kind");if(!kind.equals("income")&&!kind.equals("expense"))return;
        BigDecimal movement=kind.equals("expense")?amount.add(cost).negate():amount.subtract(cost);tx.put("postings",new JSONArray().put(Ledger.obj("account",tx.getString("account"),"currency",currency,"amount",Money.plain(movement))));
    }
    static String executionRate(String sent,String received){BigDecimal n=Money.decimal(sent),m=Money.decimal(received);if(n.signum()<=0||m.signum()<=0)throw new IllegalArgumentException("Amounts must be positive.");return Money.plain(m.divide(n,18,RoundingMode.HALF_EVEN));}
    static String received(String amount,String sourceRate,String destinationRate,String currency){BigDecimal denominator=Money.decimal(destinationRate);if(denominator.signum()<=0)throw new IllegalArgumentException("Rate must be positive");return Money.plain(Money.decimal(amount).multiply(Money.decimal(sourceRate)).divide(denominator,Money.scale(currency),RoundingMode.HALF_EVEN));}
}
