package app.vibefinancier;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Public reference quotes, never an execution price or a stablecoin peg assumption. */
public final class Rates {
    static final Map<String,String> CRYPTO=Map.of("TON","the-open-network","USDT","tether","USDC","usd-coin","BTC","bitcoin","ETH","ethereum");
    private static byte[] download(String url)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","VibeFinancier/0.1 personal finance");
        try{int code=c.getResponseCode();if(code!=200)throw new IOException("Rate provider HTTP "+code+". Existing rates were preserved.");try(InputStream in=c.getInputStream()){return Streams.read(in,2_000_000);}}finally{c.disconnect();}
    }
    private static Document cbr(LocalDate date)throws Exception {
        String suffix=date==null?"":"?date_req="+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        try{return parseCbr(download("https://www.cbr.ru/scripts/XML_daily.asp"+suffix));}catch(Exception primary){
            Exception last=primary;for(int back=0;back<(date==null?1:8);back++){String path=date==null?"":date.minusDays(back).format(DateTimeFormatter.ofPattern("'archive/'yyyy/MM/dd/"));try{return mirroredCbr(json("https://www.cbr-xml-daily.ru/"+path+"daily_json.js"));}catch(Exception e){last=e;if(e.getMessage()==null||!e.getMessage().contains("HTTP 404"))break;}}
            throw new IOException("CBR and its HTTPS mirror are unavailable. "+last.getMessage(),last);
        }
    }
    static Document mirroredCbr(JSONObject data)throws Exception {
        Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element root=doc.createElement("ValCurs");root.setAttribute("Date",LocalDate.parse(data.getString("Date").substring(0,10)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));root.setAttribute("Source","CBR via cbr-xml-daily.ru");doc.appendChild(root);JSONObject currencies=data.getJSONObject("Valute");Iterator<String> keys=currencies.keys();while(keys.hasNext()){JSONObject q=currencies.getJSONObject(keys.next());Element value=doc.createElement("Valute");for(String field:List.of("CharCode","Nominal","Value")){Element child=doc.createElement(field);child.setTextContent(q.get(field).toString());value.appendChild(child);}root.appendChild(value);}return doc;
    }
    private static String fiatSource(Document doc){String source=doc.getDocumentElement().getAttribute("Source");return source.isEmpty()?"CBR":source;}
    static Document parseCbr(byte[] bytes)throws Exception {
        if(bytes.length>2_000_000)throw new IOException("Rate XML exceeds the size limit.");for(byte b:bytes)if(b==0)throw new IOException("Unsupported rate XML encoding.");
        String markup=new String(bytes,StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);if(markup.contains("<!DOCTYPE")||markup.contains("<!ENTITY"))throw new IOException("External XML declarations are disabled.");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External XML entities are disabled.");});return builder.parse(new ByteArrayInputStream(bytes));
    }
    private static Map<String,BigDecimal> fiat(Document doc){
        Map<String,BigDecimal> rub=new HashMap<>();rub.put("RUB",BigDecimal.ONE);NodeList values=doc.getElementsByTagName("Valute");
        for(int i=0;i<values.getLength();i++){Element v=(Element)values.item(i);rub.put(v.getElementsByTagName("CharCode").item(0).getTextContent(),new BigDecimal(v.getElementsByTagName("Value").item(0).getTextContent().replace(',','.')).divide(new BigDecimal(v.getElementsByTagName("Nominal").item(0).getTextContent()),18,RoundingMode.HALF_EVEN));}return rub;
    }
    static JSONObject quote(String currency,String base,BigDecimal rate,String source,String asof){if(rate.signum()<=0)throw new IllegalArgumentException("Rate must be positive");return Ledger.obj("currency",currency,"base",base,"rate",Money.plain(rate.setScale(18,RoundingMode.HALF_EVEN)),"source",source,"asof",asof,"fetched_at",Instant.now().toString(),"manual",false);}
    private static JSONObject json(String url)throws Exception{return new JSONObject(new String(download(url),StandardCharsets.UTF_8));}
    static JSONObject latest(Store store,String currency,String base)throws Exception {if(currency.equals(base))return quote(currency,base,BigDecimal.ONE,"identity",Instant.now().toString());for(JSONObject p:store.type("rate",true))if(p.optString("currency").equals(currency)&&p.optString("base").equals(base))return p;return null;}
    public static void update(Store store)throws Exception {
        Document doc=cbr(null);Map<String,BigDecimal> rub=fiat(doc);Set<String> currencies=new TreeSet<>();String base=store.get("base","RUB");currencies.add(base);for(JSONObject a:store.type("account",true))currencies.add(a.getString("currency"));
        JSONObject prices=null;String error="";if(currencies.stream().anyMatch(CRYPTO::containsKey))try{prices=json("https://api.coingecko.com/api/v3/simple/price?ids=the-open-network,tether,usd-coin,bitcoin,ethereum&vs_currencies=usd&include_last_updated_at=true&precision=full");}catch(Exception e){error=e.getMessage();}
        if(prices!=null)for(String cur:CRYPTO.keySet())if(prices.has(CRYPTO.get(cur)))rub.put(cur,new BigDecimal(prices.getJSONObject(CRYPTO.get(cur)).get("usd").toString()).multiply(rub.get("USD")));
        if(!rub.containsKey(base))throw new IOException("No automatic quote for the base currency. Use a manual rate.");
        List<JSONObject> quotes=new ArrayList<>();for(String cur:currencies){JSONObject old=latest(store,cur,base);if(old!=null&&old.optBoolean("manual"))continue;if(!rub.containsKey(cur)){error="No automatic quote for "+cur;continue;}
            boolean crypto=CRYPTO.containsKey(cur)||CRYPTO.containsKey(base);String time=crypto&&prices!=null?Instant.ofEpochSecond(prices.getJSONObject(CRYPTO.get(CRYPTO.containsKey(cur)?cur:base)).optLong("last_updated_at")).toString():LocalDate.parse(doc.getDocumentElement().getAttribute("Date"),DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString();
            quotes.add(quote(cur,base,rub.get(cur).divide(rub.get(base),18,RoundingMode.HALF_EVEN),cur.equals(base)?"identity":crypto?"CoinGecko + "+fiatSource(doc):fiatSource(doc),time));
        }
        save(store,quotes,true);store.set("rates_updated_at",Long.toString(System.currentTimeMillis()));store.set("rates_error",error);if(!error.isEmpty())throw new IOException("Some quotes were not updated: "+error);
    }
    static void save(Store store,List<JSONObject> quotes,boolean current)throws Exception {
        Map<String,JSONObject> existing=new HashMap<>();for(JSONObject p:store.entities(false))existing.put(p.optString("id"),p);List<JSONObject> events=new ArrayList<>();
        for(JSONObject q:quotes){String key=q.getString("currency")+"."+q.getString("base");JSONObject point=point(q);String id=point.getString("id");if(!existing.containsKey(id)){events.add(Ledger.event(point,"",id));existing.put(id,point);}
            if(current){id="rate."+key;JSONObject old=existing.get(id);if(old!=null&&old.optBoolean("manual"))continue;JSONObject rate=new JSONObject(q.toString());rate.remove("_head");rate.put("id",id);rate.put("entity","rate");events.add(Ledger.event(rate,old==null?"":old.optString("_head"),UUID.randomUUID().toString()));}
        }if(!events.isEmpty())store.merge(events,true);
    }
    static JSONObject point(JSONObject q)throws Exception {
        String id="quote."+Ledger.hash(q.getString("currency")+"."+q.getString("base")+"|"+q.getString("source")+"|"+q.getString("asof")+"|"+q.getString("rate"));
        // Retrieval time differs between devices and must never enter a deterministic event payload.
        return Ledger.obj("id",id,"entity","quote","currency",q.getString("currency"),"base",q.getString("base"),"rate",q.getString("rate"),"source",q.getString("source"),"asof",q.getString("asof"));
    }
    static JSONObject historical(String currency,String base,LocalDate date)throws Exception {
        if(date.isAfter(LocalDate.now(ZoneOffset.UTC)))throw new IOException("Historical quotes are unavailable for a future date.");if(currency.equals(base))return quote(currency,base,BigDecimal.ONE,"identity",date.toString());
        Document doc=cbr(date);Map<String,BigDecimal> rub=fiat(doc);boolean crypto=CRYPTO.containsKey(currency)||CRYPTO.containsKey(base);
        if(crypto){if(date.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(364)))throw new IOException("Free crypto history is limited to the last 365 days. Supply a verified manual rate for older operations.");Set<String> requested=new HashSet<>(List.of(currency,base));for(String cur:requested)if(CRYPTO.containsKey(cur)){JSONObject value=json("https://api.coingecko.com/api/v3/coins/"+CRYPTO.get(cur)+"/history?date="+date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))+"&localization=false");rub.put(cur,new BigDecimal(value.getJSONObject("market_data").getJSONObject("current_price").get("usd").toString()).multiply(rub.get("USD")));}}
        if(!rub.containsKey(currency)||!rub.containsKey(base))throw new IOException("No historical quote for this currency pair.");
        JSONObject q=quote(currency,base,rub.get(currency).divide(rub.get(base),18,RoundingMode.HALF_EVEN),crypto?"CoinGecko daily reference + "+fiatSource(doc):fiatSource(doc)+" daily reference",date+"T00:00:00Z");q.put("granularity","daily reference");q.put("fiat_effective_date",doc.getDocumentElement().getAttribute("Date"));return q;
    }
    static JSONObject atTime(String currency,String base,LocalDateTime time,ZoneId zone)throws Exception {
        if(!CRYPTO.containsKey(currency))return historical(currency,base,time.toLocalDate());
        Instant instant=time.atZone(zone).toInstant();if(instant.isAfter(Instant.now()))throw new IOException("Historical quotes are unavailable for a future date.");if(instant.isBefore(Instant.now().minusSeconds(364L*86400)))throw new IOException("Free crypto history is limited to the last 365 days. Supply a verified manual rate for older operations.");
        if(currency.equals(base))return quote(currency,base,BigDecimal.ONE,"identity",instant.toString());
        JSONObject data=json("https://api.coingecko.com/api/v3/coins/"+CRYPTO.get(currency)+"/market_chart/range?vs_currency="+base.toLowerCase(Locale.ROOT)+"&from="+(instant.getEpochSecond()-7200)+"&to="+instant.getEpochSecond()+"&precision=full");
        return observation(currency,base,instant,data.getJSONArray("prices"));
    }
    static JSONObject observation(String currency,String base,Instant requested,JSONArray prices)throws Exception {
        JSONArray best=null;long latest=Long.MIN_VALUE;for(int i=0;i<prices.length();i++){JSONArray point=prices.getJSONArray(i);long at=point.getLong(0);if(at<=requested.toEpochMilli()&&at>latest){best=point;latest=at;}}
        if(best==null||requested.toEpochMilli()-latest>7200_000)throw new IOException("No verified observation within two hours before this operation.");
        JSONObject q=quote(currency,base,new BigDecimal(best.getString(1)),"CoinGecko historical observation",Instant.ofEpochMilli(latest).toString());q.put("granularity","last observation before operation (up to 2 hours)");return q;
    }
    static JSONObject forOperation(JSONObject p)throws Exception {
        if(p.optString("timezone").isEmpty())throw new IOException("Operation timezone is missing. An intraday quote cannot be assigned safely.");
        return atTime(p.getString("currency"),p.getString("base_currency"),LocalDateTime.parse(p.getString("time")),ZoneId.of(p.getString("timezone")));
    }
    static final class HistoricalBatch {
        interface Fetcher {JSONArray get(String currency,String base,long from,long to)throws Exception;}
        private final Map<String,long[]> bounds=new HashMap<>();private final Map<String,JSONArray> samples=new HashMap<>();private final Fetcher fetcher;
        HistoricalBatch(List<JSONObject> operations){this(operations,(cur,base,from,to)->json("https://api.coingecko.com/api/v3/coins/"+CRYPTO.get(cur)+"/market_chart/range?vs_currency="+base.toLowerCase(Locale.ROOT)+"&from="+from+"&to="+to+"&precision=full").getJSONArray("prices"));}
        HistoricalBatch(List<JSONObject> operations,Fetcher fetcher){this.fetcher=fetcher;for(JSONObject p:operations)if(p.optString("entity").equals("transaction")&&CRYPTO.containsKey(p.optString("currency"))&&!p.optString("currency").equals(p.optString("base_currency")))try{Instant at=instant(p);String key=key(p,at);long seconds=at.getEpochSecond();long[] span=bounds.get(key);if(span==null)bounds.put(key,new long[]{seconds,seconds});else{span[0]=Math.min(span[0],seconds);span[1]=Math.max(span[1],seconds);}}catch(Exception ignored){}}
        private static Instant instant(JSONObject p)throws Exception {if(p.optString("timezone").isEmpty())throw new IOException("Operation timezone is missing. An intraday quote cannot be assigned safely.");p.getString("base_currency");Instant at=LocalDateTime.parse(p.getString("time")).atZone(ZoneId.of(p.getString("timezone"))).toInstant();if(at.isAfter(Instant.now()))throw new IOException("Historical quotes are unavailable for a future date.");if(at.isBefore(Instant.now().minusSeconds(364L*86400)))throw new IOException("Free crypto history is limited to the last 365 days. Supply a verified manual rate for older operations.");return at;}
        // An 86-day window plus the two-hour lookback stays below the provider's 90-day hourly limit.
        private static String key(JSONObject p,Instant at){return p.optString("currency")+"|"+p.optString("base_currency")+"|"+Math.floorDiv(at.getEpochSecond(),86L*86400);}
        JSONObject get(JSONObject p)throws Exception {Instant at=instant(p);String key=key(p,at);JSONArray prices=samples.get(key);if(prices==null){long[] span=bounds.get(key);if(span==null)throw new IOException("Historical request window is missing.");prices=fetcher.get(p.getString("currency"),p.getString("base_currency"),span[0]-7200,span[1]+1);samples.put(key,prices);}return observation(p.getString("currency"),p.getString("base_currency"),at,prices);}
    }
    static int recent(Store store)throws Exception {
        String base=store.get("base","RUB");Set<String> currencies=new TreeSet<>();for(JSONObject a:store.type("account",false))if(!a.optString("currency").equals(base))currencies.add(a.optString("currency"));
        List<JSONObject> points=new ArrayList<>();List<String> errors=new ArrayList<>();Map<String,JSONObject> cache=new HashMap<>();
        for(String currency:currencies){if(CRYPTO.containsKey(currency)){try{JSONArray data=json("https://api.coingecko.com/api/v3/coins/"+CRYPTO.get(currency)+"/market_chart?vs_currency="+base.toLowerCase(Locale.ROOT)+"&days=7&precision=full").getJSONArray("prices");Map<LocalDate,JSONArray> days=new TreeMap<>();for(int i=0;i<data.length();i++){JSONArray p=data.getJSONArray(i);days.put(Instant.ofEpochMilli(p.getLong(0)).atOffset(ZoneOffset.UTC).toLocalDate(),p);}for(JSONArray p:days.values())points.add(quote(currency,base,new BigDecimal(p.getString(1)),"CoinGecko",Instant.ofEpochMilli(p.getLong(0)).toString()));}catch(Exception e){errors.add(currency+": "+e.getMessage());}continue;}
            for(int day=1;day<=7;day++){LocalDate date=LocalDate.now(ZoneOffset.UTC).minusDays(day);try{String key=currency+base+date;JSONObject q=cache.get(key);if(q==null){q=historical(currency,base,date);cache.put(key,q);}points.add(q);}catch(Exception e){errors.add(currency+": "+e.getMessage());break;}}}
        save(store,points,false);store.set("history_rates_error",String.join("\n",errors));return points.size();
    }
}
