package app.vibefinancier;

import org.json.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Calendar occurrences are derived from the original anchor, never the clamped prior month. */
final class Payments {
    static final List<String> FREQUENCIES=List.of("Once","Weekly","Monthly","Yearly");
    static LocalDate date(JSONObject plan,int index) {
        LocalDate anchor=LocalDate.parse(plan.optString("start"));
        return switch(plan.optString("frequency")){case "Weekly"->anchor.plusWeeks(index);case "Monthly"->anchor.plusMonths(index);case "Yearly"->anchor.plusYears(index);default->anchor;};
    }
    static String occurrence(JSONObject plan,LocalDate date){return "payment."+Ledger.hash(plan.optString("id")+"\n"+date);}
    static List<LocalDate> dates(JSONObject plan,LocalDate from,LocalDate to) {
        LocalDate start=LocalDate.parse(plan.optString("start"));
        if(to.isBefore(start)||plan.optBoolean("archived")||plan.optBoolean("deleted"))return List.of();
        String frequency=plan.optString("frequency");long offset=switch(frequency){case "Weekly"->ChronoUnit.WEEKS.between(start,from);case "Monthly"->ChronoUnit.MONTHS.between(start.withDayOfMonth(1),from.withDayOfMonth(1));case "Yearly"->from.getYear()-start.getYear();default->0;};
        int index=(int)Math.max(0,offset-1);List<LocalDate> result=new ArrayList<>();
        for(int n=0;n<10000;n++,index++) {LocalDate d=date(plan,index);if(d.isAfter(to))break;if(!d.isBefore(from))result.add(d);if(frequency.equals("Once"))break;}
        return result;
    }
    static Set<String> completed(List<JSONObject> entities){Set<String> ids=new HashSet<>();for(JSONObject p:entities)if(p.optString("entity").equals("payment_receipt"))ids.add(p.optString("id"));return ids;}
    static void validate(JSONObject p) {
        if(p.optString("name").isBlank())throw new IllegalArgumentException("Enter a payment name.");
        LocalDate.parse(p.optString("start"));if(!FREQUENCIES.contains(p.optString("frequency")))throw new IllegalArgumentException("Choose a repeat interval.");
        if(!Set.of("expense","transfer").contains(p.optString("payment_kind")))throw new IllegalArgumentException("Invalid payment kind.");
        String amount=Money.input(p.optString("amount"),p.optString("currency"));if(Money.decimal(amount).signum()<=0)throw new IllegalArgumentException("Amount must be greater than zero.");
        if(p.optInt("remind_days",-1)<0||p.optInt("remind_days")>30)throw new IllegalArgumentException("Reminder must be between 0 and 30 days.");
    }
}
