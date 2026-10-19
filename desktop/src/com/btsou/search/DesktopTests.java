package com.btsou.search;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class DesktopTests {
    private static int checks=0;
    private static void check(boolean ok,String detail){checks++;if(!ok)throw new AssertionError(detail);}
    private static String resource(String path)throws Exception{return new String(Objects.requireNonNull(DesktopTests.class.getResourceAsStream(path),path).readAllBytes(),StandardCharsets.UTF_8);}
    public static void run()throws Exception{
        List<Source> sources=Source.read(resource("/sources.json"));
        for(String id:Arrays.asList("pansearch","nyaa","3MAG","BTMOVI","ECLSO","ECLZZ")){
            Source s=sources.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow();
            String fixture=id.equals("pansearch")?"pansearch":id.equals("nyaa")?"nyaa":id;
            List<Result> results=SearchEngine.parse(s,resource("/fixtures/"+fixture+".txt"),s.url("ubuntu",1));check(!results.isEmpty(),id+" fixture parses");
            Set<String> keys=new HashSet<>();for(Result r:results){check(Result.safeUrl(r.url),"valid result URL");check(keys.add(r.key()),"deduplicated result");check(!r.title.isEmpty(),"nonempty title");}
            check(!s.url("hello world&x",2).contains("hello world&x"),"encoded search keyword");
        }
        Source pan=sources.stream().filter(s->s.id.equals("pansearch")).findFirst().orElseThrow();
        check(pan.url("test",1).endsWith("offset=0"),"pan first page");check(pan.url("test",2).endsWith("offset=10"),"pan next page");
        check(Result.cloudType("https://pan.baidu.com.evil.example/s/fake").isEmpty(),"lookalike rejected");
        check(!Result.safeUrl("file:///C:/Windows/system.ini"),"local file denied");check(!Result.safeUrl("javascript:alert(1)"),"script denied");
        check(!Result.safeUrl("https://user:password@example.com/path"),"URL credentials denied");
        Result adult=new Result();adult.title="NSFW sample";adult.url="https://example.com/";check(!adult.visible(false),"adult filtered");check(adult.visible(true),"adult explicitly enabled");
        Result normal=new Result();normal.title="Python programming";normal.url="https://example.com/";check(normal.visible(false),"ordinary result retained");
        check(Result.cloudType("https://pan.xunlei.com/s/demo?pwd=abcd").equals("迅雷"),"Xunlei classification");
        check(Result.cloudType("https://115cdn.com/s/demo").equals("115"),"115 CDN classification");
        check(Result.cloudType("https://115.com/s/demo").equals("115"),"115 classification");
        check(Result.cloudType("https://pan.xunlei.com.evil.example/s/demo").isEmpty(),"Xunlei lookalike rejected");
        String content="Thunder sample：<a href='https://pan.xunlei.com/s/aaa'>link</a> 提取码：abcd<br>115 sample：<a href='https://115.com/s/bbb?password=efgh'>link</a><br>Quark sample：<a href='https://pan.quark.cn/s/ccc'>link</a>";
        org.json.JSONObject doc=new org.json.JSONObject().put("props",new org.json.JSONObject().put("pageProps",new org.json.JSONObject().put("data",new org.json.JSONObject().put("data",new org.json.JSONArray().put(new org.json.JSONObject().put("content",content))))));
        List<Result> shares=SearchEngine.parse(pan,"<script id='__NEXT_DATA__'>"+doc+"</script>",pan.url("test",1));
        check(shares.size()==3,"new providers parsed from same index");
        check(shares.get(0).password.equals("abcd"),"Xunlei extraction code retained");
        check(shares.get(1).password.equals("efgh"),"115 password from share URL");
        check(shares.get(2).password.isEmpty(),"missing code is not invented or borrowed");
        check(DesktopFilters.codeLabel(shares.get(2)).contains("未提供"),"missing code explicitly labeled");
        check(DesktopFilters.accepts(shares.get(0),"迅雷",true,false),"provider and code filter");
        check(!DesktopFilters.accepts(shares.get(0),"115",false,false),"other provider excluded");
        check(!DesktopFilters.accepts(shares.get(2),"全部资源",true,false),"missing code excluded");
        check(DesktopFilters.accepts(shares.get(2),"夸克",false,false),"missing code visible without checkbox");
        check(!DesktopFilters.accepts(normal,"网盘",false,false),"magnet excluded from cloud filter");
        check(DesktopFilters.accepts(normal,"磁力",false,false),"magnet category");
        Result other=new Result();other.title="UC sample";other.kind="UC";
        check(DesktopFilters.accepts(other,"其他网盘",false,false),"other cloud category");
        check(!DesktopFilters.accepts(shares.get(0),"其他网盘",false,false),"major cloud excluded from other");
        Result incomplete=new Result();incomplete.url=shares.get(0).url;
        check(Result.mergeKnown(incomplete,shares.get(0)).password.equals("abcd"),"later duplicate enriches missing code");
        Result marked=new Result();marked.adult=true;Result.mergeKnown(incomplete,marked);
        check(incomplete.adult,"duplicate retains restrictive adult marking");
        String repeated="first: <a href='https://pan.baidu.com/s/duplicate'>link</a><br>second: <a href='https://pan.baidu.com/s/duplicate'>link</a> 提取码：zz99";
        doc.getJSONObject("props").getJSONObject("pageProps").getJSONObject("data").getJSONArray("data").getJSONObject(0).put("content",repeated);
        List<Result> duplicates=SearchEngine.parse(pan,"<script id='__NEXT_DATA__'>"+doc+"</script>",pan.url("test",1));
        check(duplicates.size()==1&&duplicates.get(0).password.equals("zz99"),"parser dedup preserves later extraction code");
        TapSequence sequence=new TapSequence();
        for(int i=0;i<5;i++)check(!sequence.tap(i*100_000_000L),"fewer than six clicks do not activate");
        check(sequence.tap(500_000_000L),"sixth click activates");
        check(!sequence.tap(600_000_000L),"sequence resets after activation");
        check(!sequence.tap(3_000_000_000L),"idle gap resets partial sequence");
        for(int i=1;i<5;i++)check(!sequence.tap(3_000_000_000L+i*100_000_000L),"post-timeout partial sequence");
        check(sequence.tap(3_500_000_000L),"six new clicks activate after timeout");
        TapSequence newSession=new TapSequence();check(!newSession.tap(0),"new session starts locked");
        System.out.println("DESKTOP_TESTS_OK checks="+checks);
    }
}
