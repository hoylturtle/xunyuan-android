import com.btsou.search.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class EngineTests {
    static int checks=0;
    static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
    static String read(Path p)throws Exception{return new String(Files.readAllBytes(p),"UTF-8");}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);List<Source> sources=Source.read(read(root.resolve("android/app/src/main/assets/sources.json")));
        Map<String,Source> by=new HashMap<>();for(Source s:sources)by.put(s.id,s);
        check(sources.size()==34,"source count");
        check(sources.stream().allMatch(s->s.defaultEnabled),"all configured sources enabled; adult gate remains separate");
        check(by.get("3MAG").url("中文 a&b",1).endsWith("%E4%B8%AD%E6%96%87%20a%26b"),"single-page source must not append 1");
        check(by.get("BTMOVI").url("ubuntu",2).endsWith("ubuntu_rel_2.html"),"legacy paging");
        check(by.get("pansearch").url("python",2).endsWith("keyword=python&offset=10"),"pan paging");
        check(by.get("sukebei").adult,"adult provider metadata");
        check(CloudApps.packageFor("https://pan.baidu.com/s/abc?pwd=1234#share").equals("com.baidu.netdisk"),"Baidu app route");
        check(CloudApps.packageFor("https://pan.quark.cn/s/abc").equals("com.quark.browser"),"Quark app route");
        check(CloudApps.packageFor("https://www.alipan.com/s/abc").equals("com.alicloud.databox"),"Alipan app route");
        check(CloudApps.packageFor("https://www.aliyundrive.com/s/abc").equals("com.alicloud.databox"),"legacy Aliyun app route");
        check(CloudApps.packageFor("https://pan.baidu.com.evil.test/s/abc").isEmpty(),"lookalike host must not route to disk app");
        check(CloudApps.packageFor("https://example.com/s/abc").isEmpty(),"unmapped host fallback");
        JSONObject update=new JSONObject().put("versionCode",3).put("versionName","0.2.0").put("apkUrl","https://github.com/hoylturtle/xunyuan-android/releases/download/v0.2.0/Xunyuan-0.2.0.apk").put("sha256",String.join("",Collections.nCopies(64,"a"))).put("apkSize",300000);
        UpdateInfo valid=new UpdateInfo(update.toString());check(valid.newerThan(2),"new version code upgrades");check(!valid.newerThan(3),"same version is not upgrade");check(!valid.newerThan(4),"cannot downgrade");
        for(String key:Arrays.asList("sha256","apkUrl","apkSize","versionCode")){
            JSONObject invalid=new JSONObject(update.toString());invalid.put(key,key.equals("sha256")?"invalid":key.equals("apkUrl")?"http://example.com/update.apk":-1);
            boolean bad=false;try{new UpdateInfo(invalid.toString());}catch(Exception e){bad=true;}check(bad,"reject invalid update field "+key);
        }
        String spiderFixture=read(root.resolve("research/probes/nyaa.txt"));
        // Keep the real source HTML shape, replacing only the resource title.
        org.jsoup.nodes.Document spiderDoc=org.jsoup.Jsoup.parse(spiderFixture);
        for(org.jsoup.nodes.Element link:spiderDoc.select("a[href^=/view/]"))if(!link.text().trim().isEmpty())link.text("淫荡蜘蛛侠");
        List<Result> spiderParsed=SearchEngine.parse(by.get("nyaa"),spiderDoc.html(),"https://nyaa.si/");
        check(!spiderParsed.isEmpty(),"reported title parsed from real source markup");
        for(Result r:spiderParsed)check(r.adult&&!r.visible(false)&&r.visible(true),"parsed reported title gated before display");
        boolean insecure=false;try{UpdateInfo.requireHttps("https://user:secret@example.com/a.apk");}catch(Exception e){insecure=true;}check(insecure,"no userinfo in update URL");
        check(Result.adultText("SSIS-123 sample"),"catalog adult classification");
        for(String title:Arrays.asList("淫荡蜘蛛侠", "淫蕩蜘蛛俠", "蜘蛛侠 淫 乱版", "淫·荡蜘蛛侠", "淫\u200b荡蜘蛛侠", "蜘蛛侠 ＮＳＦＷ", "蜘蛛侠 无码", "蜘蛛侠 乱伦", "蜘蛛侠 av女优")){
            Result leaked=new Result();leaked.title=title;
            check(Result.adultText(title),"adult title classified: "+title);
            check(!leaked.visible(false)&&leaked.visible(true),"mode gates title: "+title);
            check(!Result.from(leaked.json()).visible(false),"legacy saved title filtered: "+title);
        }
        for(String title:Arrays.asList("蜘蛛侠：英雄无归 1080P", "蜘蛛俠：縱橫宇宙", "超凡蜘蛛侠", "Java programming", "蜘蛛侠 动画合集"))
            check(!Result.adultText(title),"ordinary search result preserved: "+title);

        check(!Result.adultText("Python basics and Java development"),"ordinary title allowed");
        Result marked=new Result();marked.title="ordinary title";marked.adult=true;check(!marked.visible(false)&&marked.visible(true),"source flag is enforced");
        for(String bad:Arrays.asList("javascript:alert(1)","intent://x","file:///etc/passwd","https://user:pass@example.com/a","magnet:?xt=invalid","https://pan.quark.cn.evil.test/s/abc")){
            if(bad.contains("evil.test"))check(Result.cloudType(bad).isEmpty(),"reject lookalike cloud domain");else check(!Result.safeUrl(bad),"unsafe URL "+bad);
        }
        int all=0;Set<String> btKeys=new HashSet<>();
        for(String id:Arrays.asList("BTMOVI","ECLSO","ECLZZ","3MAG","CILIGEGE","nyaa","pansearch")){
            Source s=by.get(id);String data=read(root.resolve("research/probes/"+id+".txt"));List<Result> found=SearchEngine.parse(s,data,s.url(id.equals("pansearch")?"python":"ubuntu",1));
            check(!found.isEmpty(),id+" real captured response parsed");System.out.println(id+": "+found.size()+" results");all+=found.size();
            for(Result r:found){check(!r.title.isEmpty()&&Result.safeUrl(r.url),"valid result");if(r.url.startsWith("magnet:"))btKeys.add(r.key());}
            if(id.equals("pansearch")){
                Set<String> kinds=new HashSet<>();for(Result r:found)kinds.add(r.kind);
                check(kinds.contains("阿里"),"Aliyun indexed links");check(kinds.contains("夸克"),"Quark indexed links");check(kinds.contains("百度"),"Baidu indexed links");
            }
        }
        String hash="0123456789abcdef0123456789abcdef01234567";
        Result x=new Result();x.url="magnet:?xt=urn:btih:"+hash+"&dn=a";Result y=new Result();y.url="magnet:?xt=urn:btih:"+hash.toUpperCase()+"&tr=b";check(x.key().equals(y.key()),"magnet dedup ignores display and tracker");
        x.url="https://www.aliyundrive.com/s/share123?pwd=a";y.url="https://www.alipan.com/s/share123";check(x.key().equals(y.key()),"Aliyun hostname aliases dedup");
        boolean rejected=false;try{Source.read("{\"sources\":[{\"id\":\"x\",\"prefix\":\"javascript:1\"}]}");}catch(Exception e){rejected=true;}check(rejected,"unsafe config rejected");
        rejected=false;try{SearchEngine.parse(by.get("nyaa"),"<title>Just a moment...</title><p>cf-chl-</p>","https://nyaa.si");}catch(Exception e){rejected=true;}check(rejected,"challenge must not be reported as zero results");
        String content="First: <a href=\"https://pan.quark.cn/s/first\">link</a> 提取码：abcd\nSecond: <a href=\"https://pan.baidu.com/s/second\">link</a> 提取码：efgh";
        JSONObject doc=new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("data",new JSONObject().put("data",new JSONArray().put(new JSONObject().put("content",content))))));
        List<Result> passwords=SearchEngine.parse(by.get("pansearch"),"<script id='__NEXT_DATA__' type='application/json'>"+doc+"</script>","https://www.pansearch.me/search");
        check(passwords.size()==2,"multiple cloud links");check(passwords.get(0).password.equals("abcd"),"first link password");check(passwords.get(1).password.equals("efgh"),"password must not leak across links");
        System.out.println("PASS "+checks+" checks; "+all+" fixture results; "+btKeys.size()+" unique magnet hashes");
    }
}
