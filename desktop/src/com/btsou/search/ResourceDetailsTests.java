package com.btsou.search;
import java.util.*;
import java.io.*;
public final class ResourceDetailsTests {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    public static ResourceDetails sample()throws Exception{
        Result r=new Result();r.url="https://xcili.net/!kN0z";r.title="疯狂动物城2";try(InputStream input=ResourceDetailsTests.class.getResourceAsStream("/fixtures/xcili-kN0z.html")){return ResourceDetails.parse(r,new String(input.readAllBytes(),"UTF-8"));}
    }
    public static void run()throws Exception{
        ResourceDetails d=sample();check(d.magnet.startsWith("magnet:?xt=urn:btih:4c07d16365a05dde152115f1206d214ba7de1187"),"reported page primary input");check(d.magnet.contains("&dn=")&&d.magnet.contains("&xl=17719868050&"),"full encoded magnet params preserved");check(!d.magnet.contains("&amp;"),"HTML entities decoded once");check(d.files.size()==4,"main file table only, no related resources");check(d.files.get(0).contains("Zootopia")&&d.files.get(0).contains("16.5 GB"),"main file size");check(d.title.contains("疯狂动物城2"),"main heading");check(d.size.equals("16.5 GB")&&d.date.startsWith("2026-01-28"),"detail size and date");
        Result r=new Result();r.url=d.pageUrl;r.title=d.title;r.pageUrl=d.pageUrl;String old=r.key();Result saved=Result.from(r.json());saved.collectionGroup="电影";saved.note="待看";Map<String,Result> shown=new LinkedHashMap<>(),favorites=new LinkedHashMap<>();shown.put(old,r);favorites.put(old,saved);ResourceDetails.promote(r,d,shown,favorites);check(!shown.containsKey(old)&&!favorites.containsKey(old),"old webpage keys removed");check(shown.size()==1&&favorites.size()==1&&favorites.containsKey(r.key()),"both maps rekeyed by hash");check(favorites.get(r.key()).note.equals("待看")&&favorites.get(r.key()).collectionGroup.equals("电影"),"favorite metadata retained");check(r.pageUrl.equals("https://xcili.net/!kN0z"),"original webpage retained");
        Result alias=new Result();alias.url=d.pageUrl;alias.title=d.title;shown.put(alias.key(),alias);ResourceDetails.promote(alias,d,shown,favorites);check(shown.size()==1,"resolved duplicate merges by magnet hash");
        String other="magnet:?xt=urn:btih:"+String.join("",Collections.nCopies(40,"a"));Result raw=new Result();raw.url=d.pageUrl;
        boolean rejected=false;try{ResourceDetails.parse(raw,"<input id=input-magnet value='invalid'><aside class=related><a href='"+other+"'>related</a></aside>");}catch(IOException e){rejected=true;}check(rejected,"invalid primary never replaced by related magnet");
        ResourceDetails a=ResourceDetails.parse(raw,"<h1>sample</h1><a href='"+other+"'>download</a>");check(a.magnet.equals(other),"ordinary magnet anchor supported");rejected=false;try{ResourceDetails.parse(raw,"<title>安全验证</title><a href='"+other+"'>ad</a>");}catch(IOException e){rejected=true;}check(rejected,"challenge not treated as a resource");
        Result direct=new Result();direct.url=other;direct.title="known";check(ResourceDetails.load(direct).magnet.equals(other),"direct magnet needs no network");Result cloud=new Result();cloud.kind="夸克";cloud.url="https://pan.quark.cn/s/example";check(ResourceDetails.load(cloud).magnet.isEmpty(),"cloud not scraped as torrent");System.out.println("RESOURCE_DETAILS_TESTS_OK checks="+checks);
    }
}
