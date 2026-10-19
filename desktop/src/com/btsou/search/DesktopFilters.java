package com.btsou.search;

public final class DesktopFilters {
    public static final String[] TYPES={"全部资源","网盘","磁力","百度","夸克","迅雷","115","阿里","其他网盘"};
    public static boolean accepts(Result r,String type,boolean onlyWithCode,boolean allowAdult){
        if(!r.visible(allowAdult)||onlyWithCode&&(r.kind.equals("bt")||r.password.isBlank()))return false;
        if(type.equals("全部资源"))return true;
        if(type.equals("磁力"))return r.kind.equals("bt");
        if(type.equals("网盘"))return !r.kind.equals("bt");
        if(type.equals("其他网盘"))return !r.kind.equals("bt")&&!java.util.Set.of("百度","夸克","迅雷","115","阿里").contains(r.kind);
        return type.equals(r.kind);
    }
    public static String codeLabel(Result r){return r.kind.equals("bt")?"":r.password.isBlank()?"提取码：来源未提供":"提取码："+r.password;}
}
