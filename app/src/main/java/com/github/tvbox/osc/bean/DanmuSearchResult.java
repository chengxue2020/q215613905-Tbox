package com.github.tvbox.osc.bean;

import android.text.TextUtils;

public class DanmuSearchResult {
    private final String name;
    private final String url;
    private final boolean builtIn;

    public DanmuSearchResult(String name, String url, boolean builtIn) {
        this.name = name;
        this.url = url;
        this.builtIn = builtIn;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }
}
