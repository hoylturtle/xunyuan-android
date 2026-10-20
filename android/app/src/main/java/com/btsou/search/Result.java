package com.btsou.search;

import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class Result {
    public String title="",url="",pageUrl="",size="",date="",password="",source="",kind="bt";
    public String collectionGroup="",note="";
    public boolean adult;
    public String linkState="unknown",linkMessage="尚未检查";
    public long linkCheckedAt=0;
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
    private static final Pattern ADULT=Pattern.compile("(?i)(淫荡|淫蕩|淫乱|淫亂|淫秽|淫穢|淫辱|淫妻|淫女|淫兽|淫獸|淫欲|淫慾|淫靡|淫贱|淫賤|淫汁|淫穴|淫叫|淫娃|淫妇|淫婦|淫魔|淫肉|淫语|淫語|淫行|淫交|淫色|淫图|淫圖|乱伦|亂倫|强奸|強姦|强姦|迷奸|轮奸|輪姦|兽交|獸交|群交|援交|约炮|約炮|做爱|做愛|爱液|愛液|精液|射精|颜射|顏射|潮吹|自慰|手淫|肉棒|肉穴|肉便器|阴茎|陰莖|阴道|陰道|阴蒂|陰蒂|乳交|足交|深喉|调教|調教|里番|裏番|本子合集|福利姬|色情片|a片|A片|av女优|av女優|成人|色情|无码|無碼|有码|有碼|性爱|性愛|性交|裸体|裸體|巨乳|爆乳|中出|内射|內射|口交|肛交|无码|麻豆|18禁|18\\+|hentai|porn|xxx|nsfw|jav\\b|\\bsex\\b|\\b(?:SSIS|SSNI|STARS|IPX|IPZZ|MIDE|MIDV|FSDSS|JUFE|JUQ|MEYD|DASS|ABW|IPTD|HEYZO)[-_ ]?\\d{2,})");
    // Normalize full-width Latin text and remove invisible characters before matching.
    // A compact Han-only view also catches spaced/punctuated Chinese terms without
    // joining English words (e.g. a normal "sex education" title) across boundaries.
    private static final Pattern HAN_GAPS=Pattern.compile("(?<=[\\u3400-\\u9fff])[\\s\\p{P}\\p{Z}]+(?=[\\u3400-\\u9fff])");
    public static boolean adultText(String text){
        if(text==null||text.isEmpty())return false;
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("[\\p{Cf}\\p{M}]", "");
        return ADULT.matcher(normalized).find()||ADULT.matcher(HAN_GAPS.matcher(normalized).replaceAll("")).find();
    }
    public boolean visible(boolean allowAdult){return allowAdult||(!adult&&!adultText(title)&&!adultText(collectionGroup)&&!adultText(note));}
    /** Merge duplicate links without losing a code found by a later source. */
    public static Result mergeKnown(Result first,Result later){
        if(first.password.isEmpty()&&!later.password.isEmpty())first.password=later.password;
        first.adult=first.adult||later.adult;
        if(later.linkCheckedAt>first.linkCheckedAt){first.linkState=later.linkState;first.linkMessage=later.linkMessage;first.linkCheckedAt=later.linkCheckedAt;}
        return first;
    }
    public JSONObject json() throws JSONException {
        JSONObject j=new JSONObject(); j.put("collectionGroup",collectionGroup);j.put("note",note);j.put("title",title);j.put("url",url);j.put("pageUrl",pageUrl);j.put("size",size);j.put("date",date);j.put("password",password);j.put("source",source);j.put("kind",kind);j.put("adult",adult);j.put("linkState",linkState);j.put("linkMessage",linkMessage);j.put("linkCheckedAt",linkCheckedAt);return j;
    }
    public static Result from(JSONObject j){Result r=new Result();r.collectionGroup=j.optString("collectionGroup");r.note=j.optString("note");r.title=j.optString("title");r.url=j.optString("url");r.pageUrl=j.optString("pageUrl");r.size=j.optString("size");r.date=j.optString("date");r.password=j.optString("password");r.source=j.optString("source");r.kind=j.optString("kind","bt");r.adult=j.optBoolean("adult");r.linkState=j.optString("linkState","unknown");r.linkMessage=j.optString("linkMessage","尚未检查");r.linkCheckedAt=j.optLong("linkCheckedAt");return r;}
}
