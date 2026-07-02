package com.github.tvbox.osc.server;

import android.text.TextUtils;

import com.orhanobut.hawk.Hawk;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class CacheRequestProcess implements RequestProcess {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return fileName != null && fileName.startsWith("/cache");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        if (params == null) params = session.getParms();
        if (files != null && !files.isEmpty()) params.putAll(files);
        String action = params.get("do");
        String key = params.get("key");
        if (TextUtils.isEmpty(key)) return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "");
        String cacheKey = getKey(params.get("rule"), key);
        if ("get".equals(action)) return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, Hawk.get(cacheKey, ""));
        if ("set".equals(action)) {
            String value = params.get("value");
            Hawk.put(cacheKey, value == null ? "" : value);
        }
        if ("del".equals(action)) Hawk.delete(cacheKey);
        return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "OK");
    }

    private String getKey(String rule, String key) {
        return "cache_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }
}
