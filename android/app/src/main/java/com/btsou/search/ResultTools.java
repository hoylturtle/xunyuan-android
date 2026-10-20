package com.btsou.search;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Local, reversible filters. A missing type/date is not fabricated. */
public final class ResultTools {
    public static final String[] CATEGORIES={"不限制","电影","电视剧","电子书","软件"};
    public static final String[] ORDERS={"默认顺序","相关度优先","有提取码优先","最新日期优先"};
    private static final Pattern BOOK=Pattern.compile("(?i)(电子书|電子書|有声书|有聲書|小说(?:全集|合集|完结)|小說(?:全集|合集|完結)|教材|书籍|書籍|漫画|漫畫|\\bebooks?\\b|\\.(epub|mobi|azw3?|pdf|txt|djvu|cbz|cbr)(?:\\b|$))");
    private static final Pattern SOFTWARE=Pattern.compile("(?i)(软件|軟件|安装包|安裝包|便携版|便攜版|破解版|激活工具|应用程序|應用程式|\\.(exe|msi|apk|dmg|deb|rpm|appimage)(?:\\b|$)|\\b(windows|linux|ubuntu|photoshop|autocad|office|android studio|visual studio|software)\\b)");
    private static final Pattern SERIES=Pattern.compile("(?i)(电视剧|電視劇|连续剧|連續劇|短剧|短劇|美剧|美劇|韩剧|韓劇|日剧|英剧|陆剧|陸劇|台剧|台劇|港剧|港劇|剧集|劇集|第[一二三四五六七八九十百0-9]+[季集]|全[一二三四五六七八九十百0-9]+集|(?<![a-z0-9])S\\d{1,2}(?:E\\d{1,3})?(?![a-z0-9])|\\bE\\d{1,3}\\b|\\bseasons?\\s*\\d+|\\bepisodes?\\s*\\d+)");
    private static final Pattern MOVIE=Pattern.compile("(?i)(电影|電影|剧场版|劇場版|\\b(movie|film|bluray|blu-ray|bdrip|brrip|remux|webrip|web-dl|dvdrip|1080p|2160p|720p|4k)\\b|\\.(mp4|mkv|avi|mov|rmvb|m4v)(?:\\b|$))");
    public static String normalized(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\p{Cf}","");}
    public static String category(Result r){String t=normalized(r.title);if(BOOK.matcher(t).find())return "电子书";if(SOFTWARE.matcher(t).find())return "软件";if(SERIES.matcher(t).find())return "电视剧";if(MOVIE.matcher(t).find())return "电影";return "未分类";}
    public static boolean accepts(Result r,String category,String include,String exclude){
        if(!"不限制".equals(category)&&!category(r).equals(category))return false;
        String t=normalized(r.title);
        for(String word:normalized(include).trim().split("[\\s,，]+"))if(!word.isEmpty()&&!t.contains(word))return false;
        for(String word:normalized(exclude).trim().split("[\\s,，]+"))if(!word.isEmpty()&&t.contains(word))return false;
        return true;
    }
    public static int relevance(Result r,String query){String t=normalized(r.title),q=normalized(query).trim();if(q.isEmpty())return 0;int score=t.equals(q)?1000:t.contains(q)?100:0;for(String w:q.split("\\s+"))if(!w.isEmpty()&&t.contains(w))score+=10;return score;}
    private static String dateKey(Result r){Matcher m=Pattern.compile("(?<!\\d)(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)").matcher(r.date);if(!m.find())return "";try{int y=Integer.parseInt(m.group(1)),mo=Integer.parseInt(m.group(2)),d=Integer.parseInt(m.group(3));java.time.LocalDate.of(y,mo,d);return String.format(Locale.ROOT,"%04d%02d%02d",y,mo,d);}catch(Exception e){return "";}}
    public static void sort(List<Result> rows,String order,String query){
        Comparator<Result> c;
        if("相关度优先".equals(order))c=Comparator.comparingInt((Result r)->relevance(r,query)).reversed();
        else if("有提取码优先".equals(order))c=Comparator.comparing((Result r)->r.password.isEmpty());
        else if("最新日期优先".equals(order))c=Comparator.comparing((Result r)->dateKey(r)).reversed();
        else return;
        Collections.sort(rows,c); // Stable: equal/unknown values retain source order.
    }
    public static boolean favoriteMatches(Result r,String search){String t=normalized(r.title+" "+r.collectionGroup+" "+r.note);for(String w:normalized(search).trim().split("\\s+"))if(!w.isEmpty()&&!t.contains(w))return false;return true;}
    public static List<Result> importFavorites(String raw)throws org.json.JSONException{
        org.json.JSONArray a=new org.json.JSONArray(raw);if(a.length()>3000)throw new org.json.JSONException("收藏数量超过3000");List<Result> rows=new ArrayList<>();
        for(int i=0;i<a.length();i++){Result r=Result.from(a.getJSONObject(i));if(Result.safeUrl(r.url)&&!r.title.trim().isEmpty())rows.add(r);}return rows;
    }
    private ResultTools(){}
}
