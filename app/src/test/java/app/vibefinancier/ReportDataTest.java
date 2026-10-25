package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReportDataTest {
    @Test public void parentGroupsAndDrillDownKeepExactTotals()throws Exception {
        List<JSONObject> rows=new ArrayList<>(List.of(Ledger.obj("entity","category","id","food","name","Food","parent",""),Ledger.obj("entity","category","id","lunch","name","Lunch","parent","food"),Ledger.transaction("expense","a","0.1","RUB","lunch","","2026-01-02T10:00:00","RUB","1"),Ledger.transaction("expense","a","0.2","RUB","food","","2026-01-03T10:00:00","RUB","1"),Ledger.transaction("income","a","10","RUB","salary","","2026-01-01T10:00:00","RUB","1")));
        ReportData report=new ReportData(rows,"RUB","2026-01-01","2026-01-31","","");assertEquals("0.3",Money.plain(report.expense));assertEquals("10",Money.plain(report.income));assertEquals("0.3",Money.plain(report.expenses.get("food")));
        report=new ReportData(rows,"RUB","2026-01-01","2026-01-31","a","food");assertEquals("0.1",Money.plain(report.expenses.get("lunch")));assertEquals("0.2",Money.plain(report.expenses.get("food")));
    }
    @Test public void transfersDebtAndOpeningsOnlyContributeExplicitFees()throws Exception {
        List<JSONObject> rows=new ArrayList<>();for(String kind:List.of("transfer","debt","opening","adjustment")){JSONObject p=Ledger.transaction(kind,"a","100","RUB","","","2026-01-02T10:00:00","RUB","1");if(kind.equals("transfer"))p.put("fee_base","2.5");rows.add(p);}ReportData report=new ReportData(rows,"RUB","2026-01-01","2026-01-31","","");assertEquals("2.5",Money.plain(report.expense));assertEquals("0",Money.plain(report.income));assertEquals("2.5",Money.plain(report.expenses.get("__fees__")));
    }
    @Test public void missingRatesAndPeriodShiftsAreExplicit()throws Exception {
        JSONObject p=Ledger.transaction("expense","a","1","USD","","","2026-01-02T10:00:00","USD","1");ReportData report=new ReportData(List.of(p),"RUB","2026-01-01","2026-01-31","","");assertEquals(1,report.missing);assertEquals("0",Money.plain(report.expense));assertArrayEquals(new String[]{"2024-02-01","2024-02-29"},ReportData.shift("2024-01-01","2024-01-31","Month",1));
    }
}
