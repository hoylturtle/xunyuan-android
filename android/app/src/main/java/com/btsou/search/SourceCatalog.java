package com.btsou.search;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Declarative official catalog only: no downloaded scripts or executable parsers. */
public final class SourceCatalog {
    public static final int ENGINE=2,MAX_BYTES=512*1024;
    public static final long INTERVAL=86400000L;
    public static final String PRIMARY="https://raw.githubusercontent.com/hoylturtle/xunyuan-android/main/sources/catalog.json";
    public static final String FALLBACK="https://github.com/hoylturtle/xunyuan-android/releases/download/source-catalog/sources.json";
    public final long version;public final String updated,raw;public final List<Source> sources;
    public SourceCatalog(String text)throws Exception{
        if(text==null||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new Exception("来源配置超过512KB");
        JSONObject j=new JSONObject(text);if(j.optInt("schema",1)!=1||j.optInt("engine",1)>ENGINE)throw new Exception("来源格式需要新版应用，请先更新应用");
        version=j.getLong("version");if(version<1)throw new Exception("来源版本无效");updated=j.optString("updated");sources=Collections.unmodifiableList(Source.read(text));if(sources.isEmpty())throw new Exception("来源列表不能为空");raw=text;
    }
    public static SourceCatalog choose(String bundled,String cached)throws Exception{
        SourceCatalog base=new SourceCatalog(bundled);if(cached==null||cached.isEmpty())return base;
        try{SourceCatalog c=new SourceCatalog(cached);return c.version>=base.version?c:base;}catch(Exception ignored){return base;}
    }
    public int ordinaryEnabled(){int n=0;for(Source s:sources)if(s.defaultEnabled&&!s.adult)n++;return n;}
    public interface Fetcher {String get(String url)throws Exception;}
    public static final class Update {public final SourceCatalog catalog;public final boolean changed;Update(SourceCatalog c,boolean changed){catalog=c;this.changed=changed;}}
    public static Update download(SourceCatalog current)throws Exception{return download(current,url->SearchEngine.request(url,null,null,"UTF-8",MAX_BYTES));}
    public static Update download(SourceCatalog current,Fetcher fetcher)throws Exception{
        Exception last=null;
        for(String url:new String[]{PRIMARY,FALLBACK})try{
            SourceCatalog candidate=new SourceCatalog(fetcher.get(url));if(candidate.version<current.version)throw new Exception("线上来源版本较旧，保留本机配置");
            return candidate.version>current.version?new Update(candidate,true):new Update(current,false);
        }catch(Exception e){last=e;}
        throw new Exception("来源更新失败，保留当前配置："+SearchEngine.message(last));
    }
}
