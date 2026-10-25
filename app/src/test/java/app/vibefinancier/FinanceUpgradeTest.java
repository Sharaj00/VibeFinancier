package app.vibefinancier;

import org.json.*;
import org.junit.Test;
import java.math.*;
import java.util.*;
import static org.junit.Assert.*;

public class FinanceUpgradeTest {
    @Test public void historicalBatchFetchesOneWindowForManyOperations()throws Exception {
        java.time.Instant first=java.time.Instant.now().minusSeconds(10*86400).truncatedTo(java.time.temporal.ChronoUnit.HOURS);List<JSONObject> values=new ArrayList<>();for(int i=0;i<30;i++){JSONObject p=Ledger.transaction("income","crypto","1","USDT","","",java.time.LocalDateTime.ofInstant(first.plusSeconds(i*60),java.time.ZoneOffset.UTC).toString(),"RUB","75");p.put("timezone","UTC");values.add(p);}int[] requests={0};Rates.HistoricalBatch batch=new Rates.HistoricalBatch(values,(cur,base,from,to)->{requests[0]++;assertEquals("USDT",cur);assertTrue(from<=first.getEpochSecond());assertTrue(to>first.plusSeconds(29*60).getEpochSecond());return new JSONArray().put(new JSONArray().put(first.minusSeconds(60).toEpochMilli()).put("84"));});for(JSONObject p:values)assertEquals("84",batch.get(p).getString("rate"));assertEquals(1,requests[0]);
    }
    @Test public void sameQuoteFetchedOnTwoDevicesHasTheSameImmutableEvent()throws Exception {
        JSONObject a=Rates.quote("TON","RUB",new BigDecimal("100"),"fixture","2026-10-25T12:00:00Z"),b=new JSONObject(a.toString());b.put("fetched_at","2026-10-25T12:00:30Z");JSONObject aa=Rates.point(a),bb=Rates.point(b);assertEquals(aa.toString(),bb.toString());assertEquals(Ledger.event(aa,"",aa.getString("id")).getString("checksum"),Ledger.event(bb,"",bb.getString("id")).getString("checksum"));assertFalse(aa.has("fetched_at"));
    }
    @Test public void historicalObservationNeverUsesAFutureOrStaleSample()throws Exception {
        java.time.Instant at=java.time.Instant.parse("2026-10-20T12:30:00Z");JSONArray points=new JSONArray().put(new JSONArray().put(at.minusSeconds(1800).toEpochMilli()).put("100")).put(new JSONArray().put(at.plusSeconds(60).toEpochMilli()).put("999"));JSONObject q=Rates.observation("TON","RUB",at,points);assertEquals("100",q.getString("rate"));assertEquals("2026-10-20T12:00:00Z",q.getString("asof"));assertThrows(java.io.IOException.class,()->Rates.observation("TON","RUB",at,new JSONArray().put(new JSONArray().put(at.minusSeconds(7201).toEpochMilli()).put("1"))));
    }
    @Test public void mirroredCbrKeepsProviderAndNominalMetadata()throws Exception {
        var doc=Rates.mirroredCbr(Ledger.obj("Date","2026-10-23T11:30:00+03:00","Valute",Ledger.obj("USD",Ledger.obj("CharCode","USD","Nominal",1,"Value","90.1234"))));assertEquals("CBR via cbr-xml-daily.ru",doc.getDocumentElement().getAttribute("Source"));assertEquals("23.10.2026",doc.getDocumentElement().getAttribute("Date"));assertEquals("90.1234",doc.getElementsByTagName("Value").item(0).getTextContent());
    }
    @Test public void cbrXmlPreservesItsDeclaredEncodingAndRejectsExternalEntities()throws Exception {
        String xml="<?xml version=\"1.0\" encoding=\"windows-1251\"?><ValCurs Date=\"25.10.2026\"><Name>Рубль</Name></ValCurs>";
        assertEquals("Рубль",Rates.parseCbr(xml.getBytes(java.nio.charset.Charset.forName("windows-1251"))).getElementsByTagName("Name").item(0).getTextContent());
        assertThrows(java.io.IOException.class,()->Rates.parseCbr("<!DOCTYPE a [<!ENTITY x SYSTEM 'https://example.invalid'>]><a>&x;</a>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private JSONObject account(String id,String currency,String kind){return Ledger.obj("id",id,"entity","account","currency",currency,"account_type",kind,"parent","");}
    private JSONObject category(String id,String parent,String direction){return Ledger.obj("id",id,"entity","category","name",id,"parent",parent,"direction",direction);}
    private List<JSONObject> resolved(JSONObject... values)throws Exception{List<JSONObject> events=new ArrayList<>();for(JSONObject p:values)events.add(Ledger.event(p,"",p.optString("id")+".initial"));return Ledger.entities(events,false);}
    @Test public void categoryMoveUpdatesWholeSubtreeAndRejectsCycles()throws Exception {
        List<JSONObject> values=resolved(category("a","","expense"),category("b","a","expense"),category("c","b","expense"),category("income","","income"));List<JSONObject> moved=EntityHierarchy.move(values.get(1),"income","expense",values);assertEquals(2,moved.size());for(JSONObject event:moved){JSONObject p=new JSONObject(event.getString("payload"));assertEquals("income",p.getString("direction"));assertEquals(p.optString("id")+".initial",event.getString("parents"));if(p.getString("id").equals("b"))assertEquals("income",p.getString("parent"));}assertThrows(IllegalArgumentException.class,()->EntityHierarchy.move(values.get(0),"c","expense",values));
    }
    @Test public void debtMigrationDoesNotRewriteCurrencyOrPostings()throws Exception {
        List<JSONObject> values=resolved(account("debt","TON","debt"),Ledger.transaction("income","debt","3","TON","","","2026-01-01T00:00:00","RUB","100"));String before=values.get(1).toString();JSONObject p=new JSONObject(EntityHierarchy.move(values.get(0),"","crypto",values).get(0).getString("payload"));assertEquals("TON",p.getString("currency"));assertEquals("crypto",p.getString("account_type"));assertEquals(before,values.get(1).toString());
    }
    @Test public void archiveKeepsFinancialRecordsAndArchivesDescendants()throws Exception {
        List<JSONObject> values=resolved(category("a","","expense"),category("b","a","expense"));List<JSONObject> events=EntityHierarchy.archive(values.get(0),values);assertEquals(2,events.size());for(JSONObject e:events){assertTrue(new JSONObject(e.getString("payload")).getBoolean("archived"));assertFalse(new JSONObject(e.getString("payload")).optBoolean("deleted"));}
    }
    @Test public void exactFeesAddToSpendingAndKeepPrincipalSeparate()throws Exception {
        JSONObject expense=Ledger.transaction("expense","ton","0.000000003","TON","","","2026-01-01T00:00:00","RUB","200");TransactionExtras.fee(expense,"0.000000001");assertEquals("-0.000000004",expense.getJSONArray("postings").getJSONObject(0).getString("amount"));assertEquals("0.000000003",expense.getString("amount"));assertEquals("0.000000001",expense.getString("fee"));
        JSONObject income=Ledger.transaction("income","usd","100","USD","","","2026-01-01T00:00:00","RUB","90");TransactionExtras.fee(income,"2");assertEquals("98",income.getJSONArray("postings").getJSONObject(0).getString("amount"));assertEquals("180.00",income.getString("fee_base"));
    }
    @Test public void receivedAmountDerivesExecutionRateWithoutDoubleCountingFees(){assertEquals("0.01",TransactionExtras.executionRate("100","1"));assertEquals("0.5",TransactionExtras.received("100","1","200","TON"));assertThrows(IllegalArgumentException.class,()->TransactionExtras.executionRate("0","1"));}
    @Test public void fifoProfitUsesOnlyRemainingDepositsAndNetsInternalTransfers()throws Exception {
        JSONObject buy=Ledger.transaction("income","a","100","USDT","","","2026-01-01T00:00:00","RUB","80"),spend=Ledger.transaction("expense","a","40","USDT","","","2026-01-02T00:00:00","RUB","85");JSONObject transfer=Ledger.obj("id","move","entity","transaction","time","2026-01-03T00:00:00","postings",new JSONArray().put(Ledger.obj("account","a","currency","USDT","amount","-20")).put(Ledger.obj("account","b","currency","USDT","amount","20")));
        CurrencyPosition p=new CurrencyPosition(List.of(account("a","USDT","crypto"),account("b","USDT","crypto"),buy,spend,transfer),"USDT","RUB");assertEquals("60",Money.plain(p.balance));assertEquals("4800",Money.plain(p.cost));assertEquals("1200",Money.plain(p.profit(new BigDecimal("100"))));assertEquals(1,p.deposits.size());assertFalse(p.partial);
    }
    @Test public void unknownAcquisitionCostIsNeverInvented()throws Exception {
        JSONObject opening=Ledger.obj("id","opening","entity","transaction","time","2026-01-01T00:00:00","postings",new JSONArray().put(Ledger.obj("account","a","currency","TON","amount","2")));CurrencyPosition p=new CurrencyPosition(List.of(account("a","TON","crypto"),opening),"TON","RUB");assertTrue(p.partial);assertEquals("0",Money.plain(p.knownUnits));assertEquals("0",Money.plain(p.profit(new BigDecimal("100"))));assertNull(p.deposits.get(0).rate);
    }
    @Test public void fiatToCryptoDepositCostIncludesFee()throws Exception {
        JSONObject tx=Ledger.transaction("transfer","rub","1000","RUB","","","2026-01-01T00:00:00","RUB","1");tx.put("fee_base","10");tx.put("received_currency","TON");tx.put("received_amount","2");tx.put("postings",new JSONArray().put(Ledger.obj("account","rub","currency","RUB","amount","-1010")).put(Ledger.obj("account","ton","currency","TON","amount","2")));CurrencyPosition p=new CurrencyPosition(List.of(account("rub","RUB","cash"),account("ton","TON","crypto"),tx),"TON","RUB");assertEquals("1010",Money.plain(p.cost));assertEquals("190",Money.plain(p.profit(new BigDecimal("600"))));
    }
    @Test public void repairKeepsOriginalAmountsLegsAndAudit()throws Exception {
        JSONObject tx=Ledger.transaction("expense","ton","2","TON","","","2026-01-01T12:34:00","RUB","50");TransactionExtras.fee(tx,"0.1");tx.put("_head","previous");String original=tx.toString();JSONObject q=Rates.quote("TON","RUB",new BigDecimal("100"),"fixture daily","2026-01-01T00:00:00Z");JSONObject revised=RateRepair.apply(tx,q);assertEquals("200.00",revised.getString("base_amount"));assertEquals("10.00",revised.getString("fee_base"));assertEquals("50",revised.getJSONObject("rate_before_repair").getString("rate"));assertEquals(tx.getJSONArray("postings").toString(),revised.getJSONArray("postings").toString());assertEquals(original,tx.toString());assertEquals("previous",revised.getString("_head"));
    }
    @Test public void historicalRepairCachesDatesAndReportsUnavailable()throws Exception {
        JSONObject a=Ledger.transaction("income","ton","2","TON","","","2026-01-01T01:00:00","RUB","1"),b=Ledger.transaction("income","ton","3","TON","","","2026-01-01T02:00:00","RUB","1"),c=Ledger.transaction("income","ton","1","TON","","","2020-01-01T01:00:00","RUB","1");int[] calls={0};RateRepair plan=new RateRepair(List.of(a,b,c),(cur,base,date)->{calls[0]++;if(date.getYear()==2020)throw new IllegalArgumentException("Unavailable fixture");return Rates.quote(cur,base,new BigDecimal("100"),"fixture",date.toString());});assertEquals(2,calls[0]);assertEquals(2,plan.replacements.size());assertEquals(1,plan.skipped.size());assertTrue(plan.skipped.get(0).contains("Unavailable fixture"));
    }
    @Test public void indexedTimelinesMatchFullLedgerAndDoNotMutate()throws Exception {
        List<JSONObject> values=List.of(account("a","USD","cash"),Ledger.transaction("income","a","10","USD","","","2026-01-01T12:00:00","RUB","90"),Ledger.transaction("expense","a","3","USD","","","2026-01-02T12:00:00","RUB","91"));LedgerIndex index=new LedgerIndex(values);AccountTimeline full=new AccountTimeline("a",values);assertEquals(full.balance,index.timeline("a").balance);assertEquals(full.days.size(),index.timeline("a").days.size());assertEquals(full.days.get(1).closing,index.timeline("a").days.get(1).closing);assertSame(index.timeline("a"),index.timeline("a"));
    }
}
