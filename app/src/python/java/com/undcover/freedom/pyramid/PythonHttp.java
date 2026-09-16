package com.undcover.freedom.pyramid;

import org.json.JSONObject;

import java.util.Iterator;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uses the app's configured OkHttp stack for Python sites which reject the
 * embedded Python requests TLS/network fingerprint.
 */
public final class PythonHttp {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private PythonHttp() {
    }

    public static String request(String method, String url, String headersJson, String body, boolean followRedirects) {
        JSONObject result = new JSONObject();
        try {
            Request.Builder builder = new Request.Builder().url(url);
            if (headersJson != null && !headersJson.isEmpty()) {
                JSONObject headers = new JSONObject(headersJson);
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    builder.header(key, headers.optString(key));
                }
            }

            RequestBody requestBody = null;
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                requestBody = RequestBody.create(JSON, body == null ? "" : body);
            }
            Request request = builder.method(method, requestBody).build();
            okhttp3.OkHttpClient client = com.github.catvod.net.OkHttp.client();
            if (!followRedirects) {
                client = client.newBuilder().followRedirects(false).followSslRedirects(false).build();
            }
            try (Response response = client.newCall(request).execute()) {
                result.put("status_code", response.code());
                JSONObject responseHeaders = new JSONObject();
                for (String name : response.headers().names()) {
                    responseHeaders.put(name, response.header(name));
                }
                result.put("headers", responseHeaders);
                result.put("text", response.body() == null ? "" : response.body().string());
            }
        } catch (Throwable error) {
            result = new JSONObject();
            try {
                result.put("error", error.toString());
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }
}
