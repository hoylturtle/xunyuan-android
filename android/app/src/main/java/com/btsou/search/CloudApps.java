package com.btsou.search;

/** Explicit app routing keeps ordinary web-link defaults from taking precedence. */
public final class CloudApps {
    private CloudApps() {}
    public static String packageFor(String url) {
        switch (Result.cloudType(url)) {
            case "百度": return "com.baidu.netdisk";
            case "夸克": return "com.quark.browser";
            case "阿里": return "com.alicloud.databox";
            default: return "";
        }
    }
}
