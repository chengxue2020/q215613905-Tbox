package com.github.catvod.crawler;

import android.util.Base64;

import com.github.tvbox.osc.server.ControlManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpiderApi {

    public String getAddress(boolean local) {
        try {
            return ControlManager.get().getAddress(local);
        } catch (Throwable th) {
            return "";
        }
    }

    public String getPort() {
        try {
            String address = ControlManager.get().getAddress(true);
            int idx = address.lastIndexOf(":");
            return idx >= 0 ? address.substring(idx + 1).replace("/", "") : "";
        } catch (Throwable th) {
            return "";
        }
    }

    public void log(String msg) {
        try {
            SpiderDebug.log(msg);
        } catch (Throwable ignored) {
        }
    }

    public String multiReq(JsonArray array) {
        try {
            if (array == null || array.size() == 0) return "";
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(array.size(), 6));
            java.util.ArrayList<Future<String>> futures = new java.util.ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                futures.add(executor.submit(() -> request(obj)));
            }
            JsonArray result = new JsonArray();
            for (Future<String> future : futures) result.add(toResult(future.get()));
            executor.shutdown();
            return result.toString();
        } catch (Throwable th) {
            return "";
        }
    }

    public String webParse(String url, String flag) {
        try {
            if (url == null || url.isEmpty()) return "";
            String encoded = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            return "proxy://go=SuperParse&flag=" + (flag == null ? "" : flag) + "&url=" + encoded;
        } catch (Throwable th) {
            return "";
        }
    }

    private static String request(JsonObject obj) {
        try {
            String url = string(obj, "url");
            if (url.isEmpty()) return "";
            String method = string(obj, "method");
            Headers headers = headers(obj.get("headers"));
            Request.Builder builder = new Request.Builder().url(url).headers(headers);
            if ("POST".equalsIgnoreCase(method)) builder.post(body(obj));
            OkHttpClient client = com.github.catvod.net.OkHttp.client();
            try (Response response = client.newCall(builder.build()).execute()) {
                return response.body() != null ? response.body().string() : "";
            }
        } catch (Throwable th) {
            return "";
        }
    }

    private static JsonElement toResult(String text) {
        if (text == null) return new JsonPrimitive("");
        try {
            String trim = text.trim();
            if (trim.startsWith("{") || trim.startsWith("[")) {
                return JsonParser.parseString(trim);
            }
        } catch (Throwable ignored) {
        }
        return new JsonPrimitive(text);
    }

    private static RequestBody body(JsonObject obj) {
        JsonElement data = obj.get("data");
        if (data == null || data.isJsonNull()) return RequestBody.create(null, "");
        String postType = string(obj, "postType");
        if ("form".equalsIgnoreCase(postType) && data.isJsonObject()) {
            FormBody.Builder builder = new FormBody.Builder();
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
                builder.add(entry.getKey(), entry.getValue().getAsString());
            }
            return builder.build();
        }
        return RequestBody.create(null, data.isJsonPrimitive() ? data.getAsString() : data.toString());
    }

    private static Headers headers(JsonElement element) {
        try {
            if (element == null || element.isJsonNull() || !element.isJsonObject()) return new Headers.Builder().build();
            HashMap<String, String> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Headers.of(map);
        } catch (Throwable th) {
            return new Headers.Builder().build();
        }
    }

    private static String string(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
