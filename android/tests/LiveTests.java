import com.btsou.search.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import org.json.*;

public final class LiveTests {
    public static void main(String[] args)throws Exception{
        // Host test only: Android uses the phone's ordinary system connection.
        String proxy=System.getenv("HTTPS_PROXY");if(proxy!=null){URI p=new URI(proxy);for(String scheme:Arrays.asList("https","http")){System.setProperty(scheme+".proxyHost",p.getHost());System.setProperty(scheme+".proxyPort",String.valueOf(p.getPort()));}}
        Path root=Paths.get(args[0]);List<Source> sources=Source.read(new String(Files.readAllBytes(root.resolve("android/app/src/main/assets/sources.json")),"UTF-8"));JSONArray report=new JSONArray();int successes=0;Set<String> clouds=new HashSet<>();
        for(Source s:sources){if(!s.defaultEnabled||s.adult)continue;long start=System.currentTimeMillis();JSONObject entry=new JSONObject().put("id",s.id);
            try{List<Result> found=SearchEngine.fetch(s,s.id.equals("aipan")?"蜘蛛侠":s.kind.equals("pan")?"python":"ubuntu",1);if(found.isEmpty())throw new Exception("No parsed results");entry.put("results",found.size());
                if(s.kind.equals("pan"))for(Result r:found)clouds.add(r.kind);
                if(s.id.equals("3MAG")){String magnet=SearchEngine.resolveMagnet(found.get(0));if(!Result.safeUrl(magnet)||!magnet.startsWith("magnet:"))throw new Exception("Invalid magnet");entry.put("detailMagnet",true);}
                if(s.id.equals("bitsearch")||s.id.equals("pansearch")){List<Result> page2=SearchEngine.fetch(s,s.kind.equals("pan")?"python":"ubuntu",2);Set<String> keys=new HashSet<>();for(Result r:found)keys.add(r.key());int fresh=0;for(Result r:page2)if(!keys.contains(r.key()))fresh++;entry.put("page2Results",page2.size()).put("page2New",fresh);if(fresh==0)throw new Exception("Pagination yielded no new items");}
                successes++;
            }catch(Exception e){entry.put("error",SearchEngine.message(e));}
            entry.put("ms",System.currentTimeMillis()-start);report.put(entry);System.out.println(entry);
        }
        JSONObject output=new JSONObject().put("timestamp",java.time.Instant.now().toString()).put("sources",report).put("cloudTypes",clouds);Files.write(root.resolve("research/java-live-tests.json"),output.toString(2).getBytes("UTF-8"));
        if(successes!=report.length()||!clouds.contains("阿里")||!clouds.contains("夸克")||!clouds.contains("百度"))throw new AssertionError("Live default-source checks failed");
    }
}
