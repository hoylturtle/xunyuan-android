package com.btsou.search;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Plain resource metadata; never execute page scripts or include related resources. */
public final class ResourceDetails {
    public final String title,magnet,pageUrl,size,date;
    public final List<String> files;
    ResourceDetails(String title,String magnet,String pageUrl,String size,String date,List<String> files){this.title=title;this.magnet=magnet;this.pageUrl=pageUrl;this.size=size;this.date=date;this.files=Collections.unmodifiableList(new ArrayList<>(files));}
    public static boolean magnet(String value){return value!=null&&value.regionMatches(true,0,"magnet:",0,7)&&Result.safeUrl(value);}
    public static ResourceDetails known(Result r){return new ResourceDetails(r.title,magnet(r.url)?r.url:"",r.pageUrl,r.size,r.date,Collections.emptyList());}
    public static ResourceDetails load(Result r)throws Exception{
        if(!r.kind.equals("bt")||magnet(r.url))return known(r);
        if(!Result.safeUrl(r.url))throw new IOException("详情地址无效");
        return parse(r,SearchEngine.request(r.url,null,r.pageUrl,"UTF-8"));
    }
    public static ResourceDetails parse(Result r,String html)throws Exception{
        Document doc=Jsoup.parse(html,r.url);if(doc.title().contains("安全验证")||html.toLowerCase(Locale.ROOT).contains("cf-chl-"))throw new IOException("详情页需要浏览器验证");
        String value="";Element primary=doc.getElementById("input-magnet");
        if(primary!=null){value=primary.val().trim();if(!magnet(value))throw new IOException("页面主磁链无效，未使用相关推荐链接");}
        if(value.isEmpty())for(Element el:doc.select(".magnet-box input, .magnet-box textarea, input[readonly], textarea[readonly], [data-clipboard-text]")){
            if(related(el))continue;String candidate=el.hasAttr("data-clipboard-text")?el.attr("data-clipboard-text"):el.val();candidate=candidate.trim();if(magnet(candidate)){value=candidate;break;}
        }
        if(value.isEmpty())for(Element el:doc.select("a[href^=magnet:]")){if(related(el))continue;String candidate=el.attr("href").trim();if(magnet(candidate)){value=candidate;break;}}
        if(value.isEmpty())throw new IOException("详情页未返回有效磁链，可打开原网页查看");
        Element heading=doc.selectFirst(".magnet-title, h1");String title=heading==null?r.title:heading.text();List<String> files=new ArrayList<>();Element table=doc.selectFirst("table.file-list");
        if(table!=null&&!related(table))for(Element row:table.select("tr")){org.jsoup.select.Elements cells=row.select("td");if(cells.size()<2||files.size()>=500)continue;String name=cells.get(0).text(),size=cells.get(cells.size()-1).text();if(!name.isEmpty())files.add(name.substring(0,Math.min(2048,name.length()))+"  ·  "+size);}
        String size=r.size,date=r.date;Element metadata=doc.selectFirst(".magnet-box + .row");String text=metadata==null?"":metadata.text();
        Matcher m=Pattern.compile("大小[：:\\s]*([0-9.]+\\s*[KMGT]?i?B)",Pattern.CASE_INSENSITIVE).matcher(text);if(m.find())size=m.group(1);
        m=Pattern.compile("发布日期[：:\\s]*(20\\d{2}-\\d{2}-\\d{2}(?: \\d{2}:\\d{2}:\\d{2})?)").matcher(text);if(m.find())date=m.group(1);
        return new ResourceDetails(title,value,r.url,size,date,files);
    }
    private static boolean related(Element el){for(Element p=el;p!=null;p=p.parent())if(p.is("aside,nav,.related,#related,.recommend,#recommend"))return true;return false;}
    /** Rekey both maps before mutating shared Result instances. Preserve each favorite's notes. */
    public static void promote(Result r,ResourceDetails d,Map<String,Result> collected,Map<String,Result> favorites){
        if(!magnet(d.magnet))return;String old=r.key();Result shown=collected.remove(old),saved=favorites.remove(old);
        apply(r,d);if(shown!=null){apply(shown,d);collected.merge(shown.key(),shown,Result::mergeKnown);}if(saved!=null){apply(saved,d);favorites.merge(saved.key(),saved,Result::mergeKnown);}
    }
    private static void apply(Result r,ResourceDetails d){if(r.pageUrl.isEmpty())r.pageUrl=d.pageUrl;r.url=d.magnet;if(r.size.isEmpty())r.size=d.size;if(r.date.isEmpty())r.date=d.date;}
}
