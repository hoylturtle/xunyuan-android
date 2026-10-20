package com.btsou.search;

import org.json.*;
import java.net.URLEncoder;
import java.util.*;

public final class Source {
    public final String id, name, kind, parser, prefix, middle, suffix, charset, note;
    public final boolean adult, defaultEnabled, singlePage;
    public Source(JSONObject j) {
        id=j.optString("id"); name=j.optString("name",id); kind=j.optString("kind","bt");
        parser=j.optString("parser","html"); prefix=j.optString("prefix"); middle=j.optString("middle"); suffix=j.optString("suffix");
        charset=j.optString("charset","UTF-8"); adult=j.optBoolean("adult"); defaultEnabled=j.optBoolean("enabled");
        singlePage=j.optBoolean("singlePage"); note=j.optString("note");
    }
    public String url(String query,int page) throws Exception {
        String encoded=URLEncoder.encode(query,charset).replace("+","%20");
        if("aipan".equals(parser))return prefix+encoded;
        if ("pansearch".equals(parser)) return prefix+encoded+"&offset="+((page-1)*10);
        return prefix+encoded+(singlePage?"":middle+page+suffix);
    }
    public static List<Source> read(String data) throws Exception {
        JSONArray a=new JSONObject(data).getJSONArray("sources"); List<Source> out=new ArrayList<>(); Set<String> ids=new HashSet<>();
        if(a.length()>80) throw new Exception("搜索源最多 80 个");
        for(int i=0;i<a.length();i++) {
            Source s=new Source(a.getJSONObject(i));
            if(!s.id.matches("[a-zA-Z0-9_-]{1,50}")||!ids.add(s.id)) throw new Exception("来源 ID 无效或重复");
            if(!Arrays.asList("html","nyaa","nuxt","pansearch","qupansou","jikepan","pansou","bitsearch","aipan").contains(s.parser)) throw new Exception("不支持的解析器："+s.parser);
            java.net.URI u=new java.net.URI(s.prefix);
            if(!Arrays.asList("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null) throw new Exception("来源地址无效");
            if(!Arrays.asList("bt","pan").contains(s.kind)) throw new Exception("来源类型无效");
            if(!java.nio.charset.Charset.isSupported(s.charset))throw new Exception("来源编码无效："+s.charset);
            out.add(s);
        }
        return out;
    }
}
