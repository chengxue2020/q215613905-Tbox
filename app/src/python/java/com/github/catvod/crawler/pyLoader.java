package com.github.catvod.crawler;

import android.util.Log;

import com.github.catvod.crawler.python.IPyLoader;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.LOG;
import com.undcover.freedom.pyramid.PythonLoader;
import com.undcover.freedom.pyramid.PythonSpider;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class pyLoader implements IPyLoader {
    private final PythonLoader pythonLoader;
    private final ConcurrentHashMap<String, Spider> spiders;
    private String lastConfig = null; // 记录上次的配置

    public pyLoader() {
        pythonLoader = PythonLoader.getInstance().setApplication(App.getInstance());
        spiders = new ConcurrentHashMap<>();
    }

    @Override
    public void clear() {
        spiders.clear();
        pythonLoader.clear();
        lastConfig = null;
        recentPyKey = null;
    }

    @Override
    public void setConfig(String jsonStr) {
        if (jsonStr != null && !jsonStr.equals(lastConfig)) {
            Log.i("PyLoader", "echo-setConfig 初始化json ");
            pythonLoader.setConfig(jsonStr);
            lastConfig = jsonStr;
        }
    }

    private String recentPyKey;
    @Override
    public void setRecentPyKey(String key) {
        recentPyKey = key;
    }

    @Override
    public Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key)) {
            Log.i("PyLoader", "echo-getSpider spider缓存: " + key);
            return spiders.get(key);
        }
        try {
            Log.i("PyLoader", "echo-getSpider url: " + getPyUrl(cls, ext));
            Spider sp = pythonLoader.getSpider(key, getPyUrl(cls, ext));
            if (sp == null) return new SpiderNull();
            if (sp instanceof SpiderNull) return sp;
//            Log.i("PyLoader", "echo-getSpider homeContent: " + sp.homeContent(true));
            spiders.put(key, sp);
            Log.i("PyLoader", "echo-getSpider 加载spider: " + key);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params){
        return proxyInvoke(params, recentPyKey);
    }

    @Override
    public Object[] proxyInvoke(Map<String, String> params, String key){
        if(key==null || key.isEmpty())return null;
        LOG.i("echo-recentPyKey" + key);
        try {
            Spider spider = spiders.get(key);
            if (!(spider instanceof PythonSpider)) return null;
            PythonSpider originalSpider = (PythonSpider) spider;
            return originalSpider.proxyLocal(params);
        } catch (Throwable th) {
            LOG.i("echo-proxyInvoke_Throwable:---" + th.getMessage());
            th.printStackTrace();
        }
        return null;
    }

    private String getPyUrl(String api, String ext) throws UnsupportedEncodingException {
        StringBuilder urlBuilder = new StringBuilder(api);
        if (!ext.isEmpty()) {
            ext = URLEncoder.encode(ext, "UTF-8");
            urlBuilder.append(api.contains("?") ? "&" : "?").append("extend=").append(ext);
        }
        return urlBuilder.toString();
    }
}
