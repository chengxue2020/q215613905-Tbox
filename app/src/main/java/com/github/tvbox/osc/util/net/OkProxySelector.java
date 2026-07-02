package com.github.tvbox.osc.util.net;

import com.github.tvbox.osc.bean.ProxyRule;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OkProxySelector extends ProxySelector {

    private final List<ProxyRule> proxy;
    private final ProxySelector system;

    public OkProxySelector() {
        proxy = new CopyOnWriteArrayList<>();
        system = ProxySelector.getDefault();
    }

    public synchronized void addAll(List<ProxyRule> items) {
        if (items == null || items.isEmpty()) return;
        for (ProxyRule item : items) {
            if (item != null) item.init();
        }
        proxy.addAll(items);
        Collections.sort(proxy);
    }

    public synchronized void clear() {
        proxy.clear();
    }

    public List<ProxyRule> getProxy() {
        return proxy;
    }

    private List<java.net.Proxy> fallback(URI uri) {
        if (system != null && uri != null) return system.select(uri);
        List<java.net.Proxy> result = new ArrayList<>();
        result.add(java.net.Proxy.NO_PROXY);
        return result;
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        if (proxy.isEmpty() || uri == null || uri.getHost() == null) return fallback(uri);
        String host = uri.getHost();
        if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) return fallback(uri);
        for (ProxyRule item : proxy) {
            for (String rule : item.getHosts()) {
                if (rule == null) continue;
                if (containOrMatch(host, rule)) {
                    List<java.net.Proxy> proxies = item.getProxies();
                    return proxies.isEmpty() ? fallback(uri) : proxies;
                }
            }
        }
        return fallback(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        if (system != null) system.connectFailed(uri, socketAddress, e);
    }

    private boolean containOrMatch(String text, String rule) {
        try {
            return text.contains(rule) || text.matches(rule);
        } catch (Throwable th) {
            return false;
        }
    }
}
