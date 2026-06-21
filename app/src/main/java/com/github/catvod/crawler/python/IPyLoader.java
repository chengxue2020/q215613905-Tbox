package com.github.catvod.crawler.python;

import com.github.catvod.crawler.Spider;

import java.util.Map;

public interface IPyLoader {
    void clear();
    void setConfig(String jsonStr);
    void setRecentPyKey(String key);
    Spider getSpider(String key, String cls, String ext);
    Object[] proxyInvoke(Map<String, String> params);
    Object[] proxyInvoke(Map<String, String> params, String key);
}
