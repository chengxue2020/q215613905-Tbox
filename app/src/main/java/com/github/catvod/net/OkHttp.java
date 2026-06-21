package com.github.catvod.net;

import androidx.collection.ArrayMap;

import com.github.tvbox.osc.util.OkGoHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/json;q=0.9";
    private static OkDns dns;
    private static OkHttpClient client;

    public static synchronized OkDns dns() {
        if (dns == null) dns = new OkDns();
        return dns;
    }

    public static synchronized OkHttpClient client() {
        if (client != null) return client;
        OkHttpClient base = OkGoHelper.getDefaultClient();
        if (base != null) return client = base.newBuilder().dns(dns()).addInterceptor(defaultHeaders()).build();
        return client = new OkHttpClient.Builder().dns(dns()).addInterceptor(defaultHeaders()).connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS).readTimeout(TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient player() {
        return client();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        OkHttpClient base = OkGoHelper.getNoRedirectClient();
        if (base == null) base = client();
        return base.newBuilder().dns(dns()).addInterceptor(defaultHeaders()).connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, long timeout) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(client(timeout), url).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        if (url == null || !url.startsWith("http")) return "";
        try (Response res = newCall(url, headers).execute()) {
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, String tag) {
        return client.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient client, String tag) {
        if (client == null || tag == null) return;
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        if (client != null) client.dispatcher().cancelAll();
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        if (params != null) for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static Headers headers(Map<String, String> headers) {
        return headers == null ? new Headers.Builder().build() : Headers.of(headers);
    }

    private static Interceptor defaultHeaders() {
        return chain -> {
            Request request = chain.request();
            Request.Builder builder = request.newBuilder();
            if (request.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT);
            if (request.header("Accept") == null) builder.header("Accept", ACCEPT);
            return chain.proceed(builder.build());
        };
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
        if (params != null) for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }
}
