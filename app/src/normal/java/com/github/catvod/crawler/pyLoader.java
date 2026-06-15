package com.github.catvod.crawler;

import android.util.Log;

import com.github.catvod.crawler.python.IPyLoader;

import java.util.Map;

public class pyLoader implements IPyLoader {

    @Override
    public void clear() {
        Log.i("PyLoader", "java flavor: clear() called, Python is not supported.");
    }

    @Override
    public void setConfig(String jsonStr) {
        Log.i("PyLoader", "java flavor: setConfig() called, Python is not supported.");
    }

    @Override
    public void setRecentPyKey(String pyApi) {
        Log.i("PyLoader", "java flavor: setRecentPyKey() called, Python is not supported.");
    }

    @Override
    public Spider getSpider(String key, String cls, String ext) {
        Log.i("PyLoader", "java flavor: getSpider() called, Python is not supported.");
        return new SpiderNull();
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params) {
        Log.i("PyLoader", "java flavor: proxyInvoke(params) called, Python is not supported.");
        return null;
    }
}
