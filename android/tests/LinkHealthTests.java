package com.btsou.search;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LinkHealthTests {
    private static int checks=0;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    public static void run()throws Exception{
        String url="https://pan.baidu.com/s/example";long now=System.currentTimeMillis();
        for(String link:Arrays.asList(url,"https://pan.quark.cn/s/example","https://pan.xunlei.com/s/example","https://115.com/s/example","https://www.alipan.com/s/example","https://drive.uc.cn/s/example","https://cloud.189.cn/t/example","https://www.123pan.com/s/example")){
            check(LinkHealth.classify(link,200,"<h1>分享已取消</h1>",now).invalid(),"clear invalid share: "+link);
            check(LinkHealth.classify(link,404,"",now).invalid(),"missing URL: "+link);
        }
        for(int status:new int[]{401,403,429,500,502,503})check(!LinkHealth.classify(url,status,"分享已失效",now).invalid(),"restricted or service error is not deletion");
        check(!LinkHealth.classify(url,200,"<script>const error='分享已失效';</script><div id='app'></div>",now).invalid(),"unused SPA error strings ignored");
        check(!LinkHealth.classify(url,200,"安全验证：分享已失效",now).invalid(),"challenge page wins over invalid string");
        check(!LinkHealth.classify(url,200,"请输入提取码",now).invalid(),"password page not invalid");
        check(LinkHealth.classify(url,200,"请输入提取码",now).state.equals("unknown"),"password is not verified automatically");
        check(!LinkHealth.classify(url,200,"<table><thead><tr><th>文件名</th><th>文件大小</th></tr></thead><tbody><tr><td>分享已失效修复教程.pdf</td><td>下载</td></tr></tbody></table>",now).invalid(),"file title must not invalidate a share");
        check(!LinkHealth.classify("https://pan.baidu.com.evil.example/s/example",404,"",now).invalid(),"unsupported host not classified");
        check(LinkHealth.classify(url,200,"<div>文件名 文件大小 下载</div>",now).state.equals("reachable"),"file listing is reachable, not download verified");
        AtomicInteger calls=new AtomicInteger();LinkHealth cached=new LinkHealth((address,t)->{calls.incrementAndGet();return new LinkHealth.Check("invalid","deleted",t);});
        check(cached.check(url).invalid()&&cached.check(url).invalid()&&calls.get()==1,"invalid status cached");
        AtomicInteger failures=new AtomicInteger();LinkHealth flaky=new LinkHealth((address,t)->{failures.incrementAndGet();throw new java.io.IOException("offline");});
        check(flaky.check(url).state.equals("unknown")&&flaky.check(url).state.equals("unknown")&&failures.get()==1,"network errors cached as unknown");
        AtomicInteger sharedCalls=new AtomicInteger();LinkHealth shared=new LinkHealth((address,t)->{sharedCalls.incrementAndGet();Thread.sleep(100);return new LinkHealth.Check("unknown","challenge",t);});
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try{List<Future<LinkHealth.Check>> jobs=new ArrayList<>();for(int i=0;i<6;i++)jobs.add(pool.submit(()->shared.check(url)));for(Future<LinkHealth.Check> job:jobs)check(job.get(3,TimeUnit.SECONDS).state.equals("unknown"),"concurrent cache response");check(sharedCalls.get()==1,"duplicate checks coalesced");}finally{pool.shutdownNow();}
        System.out.println("LINK_HEALTH_TESTS_OK checks="+checks);
    }
    public static void main(String[] args)throws Exception{run();}
}
