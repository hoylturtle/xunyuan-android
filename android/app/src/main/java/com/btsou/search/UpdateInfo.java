package com.btsou.search;

import org.json.*;
import java.net.URI;
import java.io.IOException;
import java.util.Locale;

public final class UpdateInfo {
    public final long versionCode, apkSize;
    public final String versionName, apkUrl, sha256, notes;
    public UpdateInfo(String raw)throws Exception{
        JSONObject j=new JSONObject(raw);
        versionCode=j.getLong("versionCode");versionName=j.getString("versionName");apkUrl=j.getString("apkUrl");
        sha256=j.getString("sha256").toLowerCase(Locale.ROOT);apkSize=j.getLong("apkSize");notes=j.optString("notes","");
        if(versionCode<1||versionName.isEmpty()||versionName.length()>64||!sha256.matches("[a-f0-9]{64}")||apkSize<1||apkSize>100*1024*1024L)throw new IOException("更新信息无效");
        requireHttps(apkUrl);
    }
    public static void requireHttps(String url)throws Exception{
        URI u=new URI(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IOException("更新地址必须使用 HTTPS");
    }
    public boolean newerThan(long current){return versionCode>current;}
}
