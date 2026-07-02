package com.github.tvbox.osc.bean;

import android.net.Uri;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProxyRule implements Comparable<ProxyRule> {

    @SerializedName("name")
    private String name;
    @SerializedName("hosts")
    private List<String> hosts;
    @SerializedName("urls")
    private List<String> urls;

    private List<java.net.Proxy> proxies;
    private List<Uri> uris;
    private boolean wildcard;

    public static List<ProxyRule> arrayFrom(JsonElement element) {
        try {
            Type listType = new TypeToken<List<ProxyRule>>() {}.getType();
            List<ProxyRule> items = new Gson().fromJson(element, listType);
            return items == null ? Collections.<ProxyRule>emptyList() : items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void init() {
        wildcard = false;
        for (String host : getHosts()) {
            if (host != null && host.indexOf('*') >= 0) {
                wildcard = true;
                break;
            }
        }
        uris = new ArrayList<>();
        for (String url : getUrls()) {
            if (TextUtils.isEmpty(url)) continue;
            Uri uri = Uri.parse(url);
            if (isValid(uri)) uris.add(uri);
        }
        proxies = new ArrayList<>();
        for (Uri uri : uris) {
            java.net.Proxy proxy = create(uri);
            if (proxy != null) proxies.add(proxy);
        }
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.<String>emptyList() : hosts;
    }

    public List<String> getUrls() {
        return urls == null ? Collections.<String>emptyList() : urls;
    }

    public List<java.net.Proxy> getProxies() {
        return proxies == null ? Collections.<java.net.Proxy>emptyList() : proxies;
    }

    public String getUserInfo(String host) {
        if (uris == null || host == null) return null;
        for (Uri uri : uris) {
            if (uri == null || uri.getHost() == null) continue;
            if (host.equalsIgnoreCase(uri.getHost())) {
                String userInfo = uri.getUserInfo();
                if (!TextUtils.isEmpty(userInfo)) return userInfo;
            }
        }
        return null;
    }

    private boolean isValid(Uri uri) {
        return uri != null && uri.getScheme() != null && uri.getHost() != null && uri.getPort() > 0;
    }

    private java.net.Proxy create(Uri uri) {
        InetSocketAddress address = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
        if (isScheme(uri, "http")) return new java.net.Proxy(java.net.Proxy.Type.HTTP, address);
        if (isScheme(uri, "socks")) return new java.net.Proxy(java.net.Proxy.Type.SOCKS, address);
        return null;
    }

    private boolean isScheme(Uri uri, String scheme) {
        return uri.getScheme() != null && uri.getScheme().startsWith(scheme);
    }

    @Override
    public int compareTo(ProxyRule other) {
        return Boolean.compare(this.wildcard, other.wildcard);
    }
}
