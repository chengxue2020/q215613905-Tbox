package com.github.tvbox.osc.util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.parser.SuperParse;

import java.io.FilterInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Proxy {
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    public static Object[] proxy(Map<String, String> params) {
        try {
            String what = params.get("go");
            assert what != null;
            if (what.equals("live")) {
                return itv(params);
            }
            else if (what.equals("bom")) {
                return removeBOMFromM3U8(params);
            }
            else if (what.equals("ad")) {
                //TODO
                return null;
            }
            else if (what.equals("SuperParse")) {
                return SuperParse.loadHtml(params.get("flag"), params.get("url"));
            }

        } catch (Throwable ignored) {

        }
        return null;
    }
    public static Object[] itv(Map<String, String> params) throws Exception {
        try {
            Object[] result = new Object[4];
            String url = params.get("url");
            String type = params.get("type");
            url = URLDecoder.decode(url,"UTF-8");

            OkHttpClient client = OkGoHelper.ItvClient;
            assert type != null;
            if (type.equals("m3u8")) {
                Request request = buildRequest(url, params);
                try (Response response = executeRequest(client, request)) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        String respContent = response.body().string();
                        String finalUrl = response.request().url().toString();
                        String m3u8Content = processM3u8Content(respContent, finalUrl, params);
                        result[0] = 200;
                        result[1] = "application/vnd.apple.mpegurl";
                        result[2] = new ByteArrayInputStream(m3u8Content.getBytes("UTF-8"));
                    } else {
                        throw new IOException("M3U8 Request failed with code: " + response.code());
                    }
                }
            } else if (type.equals("ts") || type.equals("media") || type.equals("key")) {
                Request request = buildRequest(url, params);
                Response response = executeRequest(client, request);
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    result[0] = response.code();
                    result[1] = getMime(type, url, response);
                    result[2] = new ResponseInputStream(response);
                    result[3] = responseHeaders(response);
                } else {
                    int code = response.code();
                    response.close();
                    throw new IOException("Media Request failed with code: " + code);
                }
            } else {
                throw new IllegalArgumentException("Invalid type: " + type);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public static Object[] removeBOMFromM3U8(Map<String, String> params) throws Exception {
        try {
            Object[] result = new Object[3];
            String url = params.get("url");
            url = URLDecoder.decode(url,"UTF-8");

            OkHttpClient client = OkGoHelper.ItvClient;
            String redirectUrl = getRedirectedUrl(url);
//                LOG.i("echo-url"+redirectUrl);

            Request request = new Request.Builder().url(redirectUrl).build();
            try (Response response = executeRequest(client, request)) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    String m3u8Content = response.body().string();
                    // 检查并去除 UTF-8 BOM 头（BOM 为 \uFEFF）
                    if (m3u8Content.startsWith("\ufeff")) {
                        m3u8Content = m3u8Content.substring(1);
                    }
                    result[0] = 200;
                    result[1] = "application/vnd.apple.mpegurl";
                    result[2] = new ByteArrayInputStream(m3u8Content.getBytes());
                } else {
                    throw new IOException("M3U8 Request failed with code: " + response.code());
                }
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    private static Request buildRequest(String url, Map<String, String> params) {
        Request.Builder builder = new Request.Builder().url(url);
        copyHeader(builder, params, "ua", "User-Agent");
        copyHeader(builder, params, "user-agent", "User-Agent");
        copyHeader(builder, params, "User-Agent", "User-Agent");
        copyHeader(builder, params, "referer", "Referer");
        copyHeader(builder, params, "Referer", "Referer");
        copyHeader(builder, params, "origin", "Origin");
        copyHeader(builder, params, "Origin", "Origin");
        copyHeader(builder, params, "cookie", "Cookie");
        copyHeader(builder, params, "Cookie", "Cookie");
        copyHeader(builder, params, "range", "Range");
        copyHeader(builder, params, "Range", "Range");
        copyHeader(builder, params, "accept", "Accept");
        copyHeader(builder, params, "Accept", "Accept");
        copyHeader(builder, params, "accept-language", "Accept-Language");
        copyHeader(builder, params, "Accept-Language", "Accept-Language");
        return builder.build();
    }

    private static void copyHeader(Request.Builder builder, Map<String, String> params, String paramKey, String headerKey) {
        if (params == null) return;
        String value = params.get(paramKey);
        if (value == null || value.length() == 0) return;
        builder.header(headerKey, value);
    }

    private static Response executeRequest(OkHttpClient client, Request request) throws IOException {
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            System.err.println("网络请求异常：" + e.getMessage());
            throw e; // 重新抛出异常，让外层处理
        }
    }

    private static String processM3u8Content(String m3u8Content, String m3u8Url, Map<String, String> params) {
        if (m3u8Content == null) return "";
        if (m3u8Content.startsWith("\ufeff")) m3u8Content = m3u8Content.substring(1);
        String[] m3u8Lines = m3u8Content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder processedM3u8 = new StringBuilder();
        boolean nextIsVariant = false;

        for (String line : m3u8Lines) {
            String item = line.trim();
            if (item.length() == 0) {
                processedM3u8.append(line).append("\n");
            } else if (item.startsWith("#")) {
                processedM3u8.append(rewriteUriAttributes(m3u8Url, line, params)).append("\n");
                nextIsVariant = item.startsWith("#EXT-X-STREAM-INF");
            } else {
                String type = nextIsVariant || isM3u8Url(item) ? "m3u8" : "media";
                processedM3u8.append(joinUrl(m3u8Url, line, type, params)).append("\n");
                nextIsVariant = false;
            }
        }
        return processedM3u8.toString().replace("\\n\\n", "\n");
    }

    private static String rewriteUriAttributes(String base, String line, Map<String, String> params) {
        Matcher matcher = URI_ATTR.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String uri = matcher.group(1);
            String type = proxyTypeForUriAttr(line, uri);
            String replacement = "URI=\"" + joinUrl(base, uri, type, params) + "\"";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String proxyTypeForUriAttr(String line, String uri) {
        String upper = line == null ? "" : line.toUpperCase();
        if (upper.startsWith("#EXT-X-KEY") || upper.startsWith("#EXT-X-SESSION-KEY")) return "key";
        if (isM3u8Url(uri) || upper.startsWith("#EXT-X-I-FRAME-STREAM-INF")) return "m3u8";
        return "media";
    }

    private static boolean isM3u8Url(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u");
    }

    private static String joinUrl(String base, String url, String type, Map<String, String> params) {
        if (base == null) base = "";
        if (url == null) url = "";
        try {
            url = url.trim();
            if (url.startsWith("data:") || url.startsWith("blob:")) return url;
            URI baseUri = new URI(base.trim());
            URI urlUri = new URI(url);
            String proxyUrl = ControlManager.get().getAddress(true) + "proxy?go=live&type=" + type + headerQuery(params) + "&url=";
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return proxyUrl + URLEncoder.encode(urlUri.toString(),"UTF-8");
            } else if (url.startsWith("://")) {
                return proxyUrl + URLEncoder.encode(new URI(baseUri.getScheme() + url).toString(),"UTF-8");
            } else if (url.startsWith("//")) {
                return proxyUrl + URLEncoder.encode(new URI(baseUri.getScheme() + ":" + url).toString(),"UTF-8");
            } else {
                URI resolvedUri = baseUri.resolve(url);
                return proxyUrl + URLEncoder.encode(resolvedUri.toString(),"UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String headerQuery(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        appendQueryHeader(sb, params, "User-Agent", "ua");
        appendQueryHeader(sb, params, "user-agent", "ua");
        appendQueryHeader(sb, params, "Referer", "referer");
        appendQueryHeader(sb, params, "referer", "referer");
        appendQueryHeader(sb, params, "Origin", "origin");
        appendQueryHeader(sb, params, "origin", "origin");
        appendQueryHeader(sb, params, "Cookie", "cookie");
        appendQueryHeader(sb, params, "cookie", "cookie");
        return sb.toString();
    }

    private static void appendQueryHeader(StringBuilder sb, Map<String, String> params, String from, String to) throws Exception {
        if (params == null) return;
        if (sb.indexOf("&" + to + "=") >= 0) return;
        String value = params.get(from);
        if (value == null || value.length() == 0) return;
        sb.append("&").append(to).append("=").append(URLEncoder.encode(value, "UTF-8"));
    }

    private static String getMime(String type, String url, Response response) {
        String contentType = response.header("Content-Type");
        if (contentType != null && contentType.length() > 0) return contentType;
        if ("key".equals(type)) return "application/octet-stream";
        String lower = url == null ? "" : url.toLowerCase();
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        if (lower.endsWith(".m4s") || lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".vtt")) return "text/vtt";
        return "video/mp2t";
    }

    private static Map<String, String> responseHeaders(Response response) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        copyResponseHeader(response, headers, "Content-Range");
        copyResponseHeader(response, headers, "Accept-Ranges");
        copyResponseHeader(response, headers, "Content-Length");
        copyResponseHeader(response, headers, "Cache-Control");
        return headers;
    }

    private static void copyResponseHeader(Response response, Map<String, String> headers, String key) {
        String value = response.header(key);
        if (value != null && value.length() > 0) headers.put(key, value);
    }

    private static class ResponseInputStream extends FilterInputStream {
        private final Response response;

        ResponseInputStream(Response response) {
            super(response.body().byteStream());
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }

    public static String getRedirectedUrl(String url) throws IOException {
        OkHttpClient base = OkGoHelper.getDefaultClient();
        OkHttpClient client = (base != null ? base.newBuilder() : new OkHttpClient.Builder().proxySelector(OkGoHelper.proxySelector()).proxyAuthenticator(OkGoHelper.proxyAuthenticator()))
                .followRedirects(false) // 不自动跟随重定向
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isRedirect()) { // 判断是否为重定向
                return response.header("Location"); // 获取重定向后的地址
            }
            return url; // 如果没有重定向，返回原 URL
        }
    }

    public static String getM3U8Content(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        OkHttpClient client = OkGoHelper.ItvClient;
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string(); // 获取 m3u8 文件内容
            } else {
                throw new IOException("请求失败，HTTP 状态码: " + response.code());
            }
        }
    }

}
