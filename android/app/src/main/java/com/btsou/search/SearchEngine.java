package com.btsou.search;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

public final class SearchEngine {
    public interface Listener {
        void done(Source source,List<Result> results,String error);
        default void found(Source source,Result result){}
        default void removed(Source source){}
    }
    private final ExecutorService pool=Executors.newFixedThreadPool(5);
    private final ExecutorService checks=Executors.newFixedThreadPool(6);
    private final LinkHealth health=new LinkHealth();
    private final Queue<Future<?>> jobs=new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
    public synchronized void cancel(){generation.incrementAndGet();for(Future<?> f:jobs)f.cancel(true);jobs.clear();((ThreadPoolExecutor)pool).purge();((ThreadPoolExecutor)checks).purge();}
    public synchronized void search(List<Source> sources,String query,int page,boolean allowAdult,Listener listener){
        cancel();final int ticket=generation.get();
        List<Source> ordered=new ArrayList<>(sources);
        Set<String> priority=new HashSet<>(Arrays.asList("pansearch","nyaa","3MAG","BTMOVI","ECLSO","ECLZZ"));
        ordered.sort(Comparator.comparing(s->!priority.contains(s.id)));
        for(Source s:ordered)if(allowAdult||!s.adult)jobs.add(pool.submit(()->{
            try{
                List<Result> list=fetch(s,query,page);list.removeIf(r->!r.visible(allowAdult));
                if(generation.get()!=ticket||Thread.currentThread().isInterrupted())return;
                if(list.isEmpty()){listener.done(s,Collections.emptyList(),"");return;}
                List<Result> kept=Collections.synchronizedList(new ArrayList<>());
                java.util.concurrent.atomic.AtomicInteger remaining=new java.util.concurrent.atomic.AtomicInteger(list.size());
                for(Result r:list){
                    Runnable complete=()->{
                        if(generation.get()!=ticket||Thread.currentThread().isInterrupted())return;
                        if(!r.kind.equals("bt")){
                            LinkHealth.Check check=health.check(r.url);r.linkState=check.state;r.linkMessage=check.message;r.linkCheckedAt=check.checkedAt;
                        }
                        if(generation.get()!=ticket||Thread.currentThread().isInterrupted())return;
                        if(!r.linkState.equals("invalid")){kept.add(r);listener.found(s,r);}else listener.removed(s);
                        if(remaining.decrementAndGet()==0)listener.done(s,new ArrayList<>(kept),"");
                    };
                    if(r.kind.equals("bt"))complete.run();else jobs.add(checks.submit(complete));
                }
            }catch(Exception e){if(generation.get()==ticket&&!Thread.currentThread().isInterrupted())listener.done(s,Collections.emptyList(),message(e));}
        }));
    }
    public void close(){cancel();pool.shutdownNow();checks.shutdownNow();}
    public static String message(Exception e){
        if(e instanceof SocketTimeoutException)return "连接超时";
        if(e instanceof UnknownHostException)return "域名无法解析";
        if(e instanceof javax.net.ssl.SSLException)return "TLS 连接失败";
        String m=e.getMessage();return m==null?e.getClass().getSimpleName():m.substring(0,Math.min(120,m.length()));
    }
    public static String request(String address,String body,String referer,String charset) throws Exception {
        for(int redirect=0;redirect<5;redirect++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(!Result.safeUrl(address)||address.startsWith("magnet:"))throw new IOException("地址无效");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept","text/html,application/json;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Encoding","gzip");
            if(referer!=null)c.setRequestProperty("Referer",referer);
            try {
                if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream os=c.getOutputStream()){os.write(body.getBytes("UTF-8"));}}
                int status=c.getResponseCode();
                if(status>=300&&status<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("跳转缺少地址");String next=new URL(new URL(address),loc).toString();if(address.startsWith("https:")&&next.startsWith("http:"))throw new IOException("拒绝 HTTPS 降级跳转");address=next;if(status==303){body=null;}continue;}
                if(status!=200)throw new IOException("HTTP "+status);
                InputStream raw=c.getInputStream();
                try(InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[8192];int n;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
                    while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(System.nanoTime()>deadline)throw new SocketTimeoutException();if(out.size()+n>4*1024*1024)throw new IOException("来源响应超过 4 MB");out.write(b,0,n);}
                    return new String(out.toByteArray(),Charset.forName(charset));
                }
            }finally{c.disconnect();}
        }
        throw new IOException("跳转次数过多");
    }
    public static List<Result> fetch(Source s,String query,int page)throws Exception{
        String url=s.url(query,page),body=null,referer=s.prefix;
        if("qupansou".equals(s.parser)){
            url=s.prefix;referer="https://pan.funletu.com/";
            JSONObject q=new JSONObject();for(String k:Arrays.asList("id","datetime","categoryid","filetypeid","filetype","reportid","validid"))q.put(k,"");q.put("courseid",1);q.put("searchtext",query);
            body=new JSONObject().put("style","get").put("datasrc","search").put("query",q).put("page",new JSONObject().put("pageSize",20).put("pageIndex",page)).put("order",new JSONObject().put("prop","sort").put("order","desc")).put("message","请求资源列表数据").toString();
        }else if("jikepan".equals(s.parser)){
            if(page>1)return Collections.emptyList();url=s.prefix;referer="https://jikepan.xyz/";body=new JSONObject().put("name",query).put("is_all",false).toString();
        }
        String data=request(url,body,referer,s.charset);
        return parse(s,data,url);
    }
    private static Result base(Source s){Result r=new Result();r.source=s.name;r.adult=s.adult;r.kind=s.kind;return r;}
    public static List<Result> parse(Source s,String data,String url)throws Exception{
        List<Result> out=new ArrayList<>();
        if("qupansou".equals(s.parser)){
            JSONObject j=new JSONObject(data);if(j.optInt("status")!=200)throw new IOException("来源拒绝请求："+j.optString("message"));JSONArray a=j.optJSONArray("data");
            if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);Result r=base(s);r.title=clean(x.optString("title",x.optString("filename")));r.url=x.optString("url");if(r.url.isEmpty())r.url=x.optString("link");r.password=x.optString("extcode");r.size=x.optString("size");r.date=x.optString("updatetime");r.kind=Result.cloudType(r.url);if(!r.kind.isEmpty())out.add(r);}
        }else if("jikepan".equals(s.parser)){
            JSONArray a=new JSONObject(data).optJSONArray("list");if(a==null)throw new IOException("来源数据格式已改变");
            for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);JSONArray links=item.optJSONArray("links");if(links!=null)for(int k=0;k<links.length();k++){JSONObject link=links.getJSONObject(k);Result r=base(s);r.title=clean(item.optString("name"));r.url=link.optString("link");r.password=link.optString("pwd");r.kind=Result.cloudType(r.url);if(!r.kind.isEmpty())out.add(r);}}
        }else{
            Document doc=Jsoup.parse(data,url);
            String lower=data.toLowerCase(Locale.ROOT);
            if(lower.contains("cf-chl-")||lower.contains("just a moment...")||doc.title().contains("安全验证"))throw new IOException("需要浏览器验证");
            if("pansearch".equals(s.parser)){
                Element script=doc.getElementById("__NEXT_DATA__");if(script==null)throw new IOException("网盘来源结构已改变或需要验证");
                JSONArray a=new JSONObject(script.data()).getJSONObject("props").getJSONObject("pageProps").getJSONObject("data").getJSONArray("data");
                for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);String content=item.optString("content");Document frag=Jsoup.parse(content.replace("\n","<br>"));
                    String description=frag.text();
                    for(Element link:frag.select("a[href]")){
                        String target=link.attr("href");String type=Result.cloudType(target);if(type.isEmpty())continue;
                        Result r=base(s);r.url=target;r.kind=type;r.pageUrl=url;r.date=item.optString("time");r.adult=r.adult||Result.adultText(description);
                        // Preserve the label immediately before each link instead of assigning a whole collection title.
                        String label="";Node prev=link.previousSibling();int guard=0;
                        while(prev!=null&&guard++<10){if(prev instanceof Element&&((Element)prev).tagName().equals("br"))break;label=prev.toString()+label;prev=prev.previousSibling();}
                        r.title=clean(label).replaceAll("^[\\s;；\\d、.]+","").replaceAll("[:：]$","");
                        if(r.title.length()<2)r.title=description.substring(0,Math.min(100,description.length()));
                        String tail="";Node next=link.nextSibling();guard=0;
                        while(next!=null&&guard++<10){if(next instanceof Element&&((Element)next).tagName().equals("br"))break;tail+=next.toString();next=next.nextSibling();}
                        Matcher pass=Pattern.compile("(?:提取码|密码|提取碼)[：:\\s]*([a-zA-Z0-9]{4,8})").matcher(clean(label+" "+tail));if(pass.find())r.password=pass.group(1);
                        if(r.password.isEmpty())try{Matcher pm=Pattern.compile("(?:[?&])(?:pwd|password)=([a-zA-Z0-9]{4,8})(?:&|$)").matcher(target);if(pm.find())r.password=pm.group(1);}catch(Exception ignored){}
                        out.add(r);
                    }
                }
            }else if("nuxt".equals(s.parser)){
                Element script=doc.getElementById("__NUXT_DATA__");if(script==null)throw new IOException("来源结构已改变");JSONArray pool=new JSONArray(script.data());
                for(int i=0;i<pool.length();i++){JSONObject o=pool.optJSONObject(i);if(o==null||!o.has("infoHash")||!o.has("name"))continue;
                    String hash=ref(pool,o,"infoHash");if(!hash.matches("[a-fA-F0-9]{40}"))continue;Result r=base(s);r.title=clean(ref(pool,o,"name"));r.url="magnet:?xt=urn:btih:"+hash;r.pageUrl=url;r.date=ref(pool,o,"createTime");r.size=humanSize(ref(pool,o,"length"));out.add(r);}
            }else{
                Elements blocks;
                if("nyaa".equals(s.parser))blocks=doc.select("table.torrent-list tbody tr");
                else {blocks=doc.select(".search-item, .file-list tbody tr");if(blocks.isEmpty())blocks=doc.select("h2, h3");}
                for(Element b:blocks){
                    Element title="nyaa".equals(s.parser)?b.selectFirst("td[colspan] a[href^=/view/]:not(.comments)"):b.selectFirst("h2 a, h3 a, .result-title a");
                    if(title==null)title=b.selectFirst("a[href]");if(title==null||title.text().trim().isEmpty())continue;
                    Result r=base(s);r.title=title.text().trim();r.pageUrl=title.absUrl("href");Element mag=b.selectFirst("a[href^=magnet:]");
                    if(mag!=null)r.url=mag.attr("href");else{
                        Matcher h=Pattern.compile("(?:/)([a-fA-F0-9]{40})(?:[./?#]|$)").matcher(r.pageUrl);
                        r.url=h.find()?"magnet:?xt=urn:btih:"+h.group(1):r.pageUrl;
                    }
                    String text=b.text();Matcher sz=Pattern.compile("(?i)\\b[0-9]+(?:\\.[0-9]+)?\\s*(?:[KMGT]i?B)\\b").matcher(text);if(sz.find())r.size=sz.group();
                    Matcher dt=Pattern.compile("20\\d{2}[-/]\\d{2}[-/]\\d{2}").matcher(text);if(dt.find())r.date=dt.group();
                    if(r.url.startsWith("magnet:")||!r.pageUrl.equals(url)&&r.pageUrl.matches("https?://[^/]+/(?:!|hash/|bt/|cili/|detail/|view/).+"))out.add(r);
                }
                if(out.isEmpty()&&blocks.isEmpty()&&!lower.contains("没有")&&!lower.contains("no results")&&!lower.contains("0 个结果"))throw new IOException("来源页面无法解析或结构已改变");
            }
        }
        LinkedHashMap<String,Result> dedup=new LinkedHashMap<>();
        for(Result r:out)if(!r.title.isEmpty()&&Result.safeUrl(r.url)){r.adult=r.adult||Result.adultText(r.title);dedup.merge(r.key(),r,Result::mergeKnown);}
        return new ArrayList<>(dedup.values());
    }
    private static String ref(JSONArray pool,JSONObject obj,String key){int index=obj.optInt(key,-1);return index>=0&&index<pool.length()?pool.optString(index):"";}
    public static String clean(String html){return Jsoup.parse(html).text().trim();}
    private static String humanSize(String s){try{double n=Double.parseDouble(s);String[] u={"B","KB","MB","GB","TB"};int k=0;while(n>=1024&&k<4){n/=1024;k++;}return String.format(Locale.ROOT,"%.2f %s",n,u[k]);}catch(Exception e){return s;}}
    public static String resolveMagnet(Result r)throws Exception{
        if(r.url.startsWith("magnet:"))return r.url;
        Document d=Jsoup.parse(request(r.url,null,r.pageUrl,"UTF-8"),r.url);Element a=d.selectFirst("a[href^=magnet:]");
        if(a!=null&&Result.safeUrl(a.attr("href")))return a.attr("href");
        throw new IOException("详情页未返回磁链，可在浏览器中查看");
    }
}
