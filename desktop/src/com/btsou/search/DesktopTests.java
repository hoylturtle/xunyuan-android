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
        System.out.println("DESKTOP_TESTS_OK checks="+checks);
    }
}
