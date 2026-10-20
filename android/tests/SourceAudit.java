import com.btsou.search.*;
import org.json.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only periodic audit. A transient failure never rewrites the official catalog. */
public final class SourceAudit {
    public static void main(String[] args)throws Exception{
        String proxy=System.getenv("HTTPS_PROXY");if(proxy!=null&&!proxy.isEmpty()){URI p=new URI(proxy);for(String scheme:Arrays.asList("https","http")){System.setProperty(scheme+".proxyHost",p.getHost());System.setProperty(scheme+".proxyPort",String.valueOf(p.getPort()));}}
        Path root=Paths.get(args[0]);SourceCatalog catalog=new SourceCatalog(new String(Files.readAllBytes(root.resolve("sources/catalog.json")),"UTF-8"));ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<JSONObject>> jobs=new ArrayList<>();
        for(Source s:catalog.sources)if(s.defaultEnabled&&!s.adult)jobs.add(pool.submit(()->{long start=System.nanoTime();String q=s.id.equals("aipan")?"蜘蛛侠":s.kind.equals("pan")?"python":"ubuntu";JSONObject row=new JSONObject().put("id",s.id).put("query",q);try{List<Result> found=SearchEngine.fetch(s,q,1);row.put("results",found.size()).put("state",found.isEmpty()?"empty":"parsed");if(found.isEmpty())row.put("note","页面可解析但未得到结果，不等于来源永久失效");}catch(Exception e){row.put("state","unconfirmed").put("error",SearchEngine.message(e));}row.put("ms",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));System.out.println(row);return row;}));
        JSONArray rows=new JSONArray();for(Future<JSONObject> job:jobs)try{rows.put(job.get(65,TimeUnit.SECONDS));}catch(Exception e){job.cancel(true);rows.put(new JSONObject().put("state","unconfirmed").put("error","Audit budget exceeded"));}pool.shutdownNow();
        JSONObject report=new JSONObject().put("checkedAt",java.time.Instant.now().toString()).put("catalogVersion",catalog.version).put("sources",rows).put("note","Cloud probe only; no account login, share download or automatic retirement");Path output=root.resolve("research/live-source-refresh.json");Files.write(output,report.toString(2).getBytes("UTF-8"));
    }
}
