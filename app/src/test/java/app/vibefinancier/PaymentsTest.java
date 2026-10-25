package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class PaymentsTest {
    private JSONObject plan(String date,String repeat){return Ledger.obj("id","rent","entity","payment_plan","name","Rent","start",date,"frequency",repeat,"payment_kind","expense","amount","100","currency","RUB","remind_days",1);}
    @Test public void monthlyAnchorReturnsTo31AfterFebruary(){JSONObject p=plan("2024-01-31","Monthly");assertEquals(List.of(LocalDate.of(2024,2,29),LocalDate.of(2024,3,31),LocalDate.of(2024,4,30)),Payments.dates(p,LocalDate.of(2024,2,1),LocalDate.of(2024,4,30)));}
    @Test public void leapYearAndWeeklyIntervalsStayAnchored(){JSONObject p=plan("2024-02-29","Yearly");assertEquals(LocalDate.of(2028,2,29),Payments.date(p,4));assertEquals(LocalDate.of(2024,3,7),Payments.date(plan("2024-02-29","Weekly"),1));}
    @Test public void oneOffAndArchivedAndFutureRanges()throws Exception{JSONObject p=plan("2026-10-25","Once");assertEquals(1,Payments.dates(p,LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)).size());assertTrue(Payments.dates(p,LocalDate.of(2026,1,1),LocalDate.of(2026,2,1)).isEmpty());p.put("archived",true);assertTrue(Payments.dates(p,LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)).isEmpty());}
    @Test public void identityIsIndependentOfAmountAndTimezone()throws Exception{JSONObject p=plan("2026-10-25","Monthly");String id=Payments.occurrence(p,LocalDate.of(2026,10,25));p.put("amount","200");assertEquals(id,Payments.occurrence(p,LocalDate.of(2026,10,25)));assertNotEquals(id,Payments.occurrence(p,LocalDate.of(2026,11,25)));}
    @Test public void rejectsInvalidAmountsAndReminderRanges()throws Exception{JSONObject p=plan("2026-10-25","Monthly");Payments.validate(p);p.put("amount","-1");assertThrows(IllegalArgumentException.class,()->Payments.validate(p));p.put("amount","1");p.put("remind_days",31);assertThrows(IllegalArgumentException.class,()->Payments.validate(p));}
    @Test public void remindersRespectMorningAndCompletedOccurrences(){JSONObject p=plan("2026-10-25","Monthly");assertTrue(PaymentReminderWorker.due(List.of(p),LocalDateTime.of(2026,10,24,8,59)).isEmpty());assertEquals(1,PaymentReminderWorker.due(List.of(p),LocalDateTime.of(2026,10,24,9,0)).size());JSONObject receipt=Ledger.obj("id",Payments.occurrence(p,LocalDate.of(2026,10,25)),"entity","payment_receipt");assertTrue(PaymentReminderWorker.due(List.of(p,receipt),LocalDateTime.of(2026,10,25,12,0)).isEmpty());}
}
