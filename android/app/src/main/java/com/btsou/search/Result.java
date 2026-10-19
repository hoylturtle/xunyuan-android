package com.btsou.search;

import java.net.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class Result {
    public String title="",url="",pageUrl="",size="",date="",password="",source="",kind="bt";
    public boolean adult;
    public static String cloudType(String link) {
        try {
            String host=new URI(link).getHost(); if(host==null)return "";host=host.toLowerCase(Locale.ROOT);
            if(host.equals("pan.baidu.com"))return "百度";
            if(host.equals("pan.quark.cn"))return "夸克";
            if(host.equals("www.alipan.com")||host.equals("alipan.com")||host.equals("www.aliyundrive.com")||host.equals("aliyundrive.com"))return "阿里";
            if(host.equals("drive.uc.cn"))return "UC";
            if(host.equals("cloud.189.cn"))return "天翼";
            if(host.equals("www.123pan.com")||host.equals("www.123684.com")||host.equals("www.123865.com")||host.equals("www.123912.com")||host.equals("www.123pan.cn"))return "123";
            if(host.equals("pan.xunlei.com"))return "迅雷";
            if(host.equals("115.com")||host.equals("115cdn.com"))return "115";
        } catch(Exception ignored){}
        return "";
    }
    public static boolean safeUrl(String value) {
        try {
            URI u=new URI(value);String scheme=u.getScheme();
            if("magnet".equalsIgnoreCase(scheme))return Pattern.compile("(?i)(?:[?&])xt=urn:btih:([a-f0-9]{40}|[a-z2-7]{32})(?:&|$)").matcher(value).find();
            return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))&&u.getHost()!=null&&u.getUserInfo()==null;
        }catch(Exception e){return false;}
    }
    public String key() {
        Matcher m=Pattern.compile("(?i)urn:btih:([a-f0-9]{40}|[a-z2-7]{32})").matcher(url);
        if(m.find())return "bt:"+m.group(1).toLowerCase(Locale.ROOT);
        try {
            URI u=new URI(url);String h=u.getHost().toLowerCase(Locale.ROOT);
            if(h.endsWith("aliyundrive.com")||h.endsWith("alipan.com"))h="alipan.com";
            return h+u.getPath().replaceAll("/+$","");
        } catch(Exception e){return url;}
    }
    private static final Pattern ADULT=Pattern.compile("(?i)(成人|色情|无码|無碼|有码|有碼|性爱|性愛|性交|裸体|裸體|巨乳|爆乳|中出|内射|內射|口交|肛交|无码|麻豆|18禁|18\\+|hentai|porn|xxx|nsfw|jav\\b|\\bsex\\b|\\b(?:SSIS|SSNI|STARS|IPX|IPZZ|MIDE|MIDV|FSDSS|JUFE|JUQ|MEYD|DASS|ABW|IPTD|HEYZO)[-_ ]?\\d{2,})");
    public static boolean adultText(String text){return ADULT.matcher(text).find();}
    public boolean visible(boolean allowAdult){return allowAdult||(!adult&&!adultText(title));}
    /** Merge duplicate links without losing a code found by a later source. */
    public static Result mergeKnown(Result first,Result later){
        if(first.password.isEmpty()&&!later.password.isEmpty())first.password=later.password;
        first.adult=first.adult||later.adult;
        return first;
    }
    public JSONObject json() throws JSONException {
        JSONObject j=new JSONObject(); j.put("title",title);j.put("url",url);j.put("pageUrl",pageUrl);j.put("size",size);j.put("date",date);j.put("password",password);j.put("source",source);j.put("kind",kind);j.put("adult",adult);return j;
    }
    public static Result from(JSONObject j){Result r=new Result();r.title=j.optString("title");r.url=j.optString("url");r.pageUrl=j.optString("pageUrl");r.size=j.optString("size");r.date=j.optString("date");r.password=j.optString("password");r.source=j.optString("source");r.kind=j.optString("kind","bt");r.adult=j.optBoolean("adult");return r;}
}
