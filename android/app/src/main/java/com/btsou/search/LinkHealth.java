package com.btsou.search;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Conservative public-share checks: an error page is not a successful share. */
public final class LinkHealth {
    public static final class Check {
        public final String state,message;public final long checkedAt;
        Check(String state,String message,long now){this.state=state;this.message=message;checkedAt=now;}
        public boolean invalid(){return state.equals("invalid");}
    }
    interface Transport {Check get(String url,long now)throws Exception;}
    private final Transport transport;
    private final Map<String,Check> cache=new LinkedHashMap<String,Check>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Check> e){return size()>512;}};
    private final ConcurrentMap<String,FutureTask<Check>> inflight=new ConcurrentHashMap<>();
    public LinkHealth(){this(LinkHealth::request);}
    LinkHealth(Transport transport){this.transport=transport;}
    public static boolean cloud(String url){return !Result.cloudType(url).isEmpty();}
    public Check check(String url){
        long now=System.currentTimeMillis();
        if(!cloud(url))return new Check("unknown","不是支持的网盘分享地址",now);
        synchronized(cache){Check old=cache.get(url);if(old!=null&&now-old.checkedAt<(old.state.equals("unknown")?60000:300000))return old;}
        FutureTask<Check> task=new FutureTask<>(()->transport.get(url,now));
        FutureTask<Check> other=inflight.putIfAbsent(url,task);boolean owner=other==null;if(!owner)task=other;
        try{
            if(owner)task.run();Check checked=task.get();
            if(!Thread.currentThread().isInterrupted())synchronized(cache){cache.put(url,checked);}
            return checked;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return new Check("unknown","检查已取消",now);}
        catch(Exception e){Check failed=new Check("unknown","连接失败或检查超时",now);synchronized(cache){cache.put(url,failed);}return failed;}
        finally{if(owner)inflight.remove(url,task);}
    }
    public static Check classify(String url,int status,String html,long now){
        if(!cloud(url))return new Check("unknown","不支持的网盘地址",now);
        // Authentication, throttling and access challenges are never proof of deletion.
        if(status==401||status==403||status==429||status>=500)return new Check("unknown","访问受限，暂未确认",now);
        if(status==404||status==410)return new Check("invalid","分享地址已失效（HTTP "+status+"）",now);
        if(status!=200)return new Check("unknown","暂未确认（HTTP "+status+"）",now);
        Document document=Jsoup.parse(html);document.select("script,style,noscript,template,[hidden],[aria-hidden=true]").remove();
        for(org.jsoup.nodes.Element element:document.select("[style]")){String style=element.attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+","");if(style.contains("display:none")||style.contains("visibility:hidden"))element.remove();}
        String text=document.text();
        if(text.matches("(?is).*(验证码|安全验证|人机验证|访问频繁|登录后|請登錄|captcha|just a moment|access denied).*"))return new Check("unknown","需要验证或登录，暂未确认",now);
        if(!document.select("input[type=password],input[placeholder*=提取码],input[name=pwd]").isEmpty())return new Check("unknown","页面需密码，尚未验证",now);
        // Inspect visible page text only. SPA bundles frequently contain unused error messages.
        boolean fileRows=!document.select("table tbody tr,[data-file-id],.file-list-item,.file-item").isEmpty();
        if(!fileRows&&text.length()<1500&&text.matches("(?s).*(分享(?:的文件)?(?:已经被|已被|已经|已)?(?:取消|删除|刪除|失效|过期|過期)|分享链接(?:已失效|不存在|已过期)|分享的文件不存在|链接(?:不存在|已失效|已过期)|文件已被分享者删除|分享内容已被取消|该分享已取消|来晚了.*分享.*(?:失效|取消)|分享文件已取消).*"))return new Check("invalid","分享页面明确提示失效",now);
        if(text.matches("(?s).*(提取码|提取碼|访问码|访问密码).*"))return new Check("unknown","页面需提取码，尚未验证密码",now);
        // A rendered file list provides better evidence than a generic HTTP 200 SPA shell.
        if(text.matches("(?s).*(文件名|文件名称).*(文件大小|修改时间|下载|下載).*"))return new Check("reachable","分享页面有文件列表（未验证下载）",now);
        return new Check("unknown","页面可连接，分享状态暂未确认",now);
    }
    private static Check request(String address,long now)throws Exception{
        String kind=Result.cloudType(address);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        for(int hops=0;hops<4;hops++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            URI uri=new URI(address);
            if(uri.getUserInfo()!=null||!"https".equalsIgnoreCase(uri.getScheme())||!kind.equals(Result.cloudType(address)))return new Check("unknown","跨站跳转，暂未确认",now);
            int budget=(int)Math.min(4000,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));if(budget<=0)return new Check("unknown","检查超时，暂未确认",now);
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(budget);c.setReadTimeout(budget);c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36");
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)return new Check("unknown","跳转缺少地址",now);address=new URL(new URL(address),next).toString();continue;}
                if(status!=200)return classify(address,status,"",now);
                String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).contains("text/html"))return new Check("unknown","非网页响应，暂未确认",now);
                try(InputStream input=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(bytes.size()+n>1024*1024||System.nanoTime()>deadline)return new Check("unknown","检查超出时间或大小限制",now);bytes.write(buffer,0,n);}
                    return classify(address,status,new String(bytes.toByteArray(),"UTF-8"),now);
                }
            }finally{c.disconnect();}
        }
        return new Check("unknown","跳转次数过多",now);
    }
}
