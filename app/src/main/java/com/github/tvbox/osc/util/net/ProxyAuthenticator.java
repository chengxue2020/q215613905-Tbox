package com.github.tvbox.osc.util.net;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.bean.ProxyRule;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import java.net.InetSocketAddress;

public class ProxyAuthenticator implements okhttp3.Authenticator {

    private final OkProxySelector selector;

    public ProxyAuthenticator(OkProxySelector selector) {
        this.selector = selector;
    }

    @Override
    public Request authenticate(Route route, @NonNull Response response) {
        if (route == null || response.request().header("Proxy-Authorization") != null) return null;
        if (!(route.proxy().address() instanceof InetSocketAddress)) return null;
        InetSocketAddress proxyAddress = (InetSocketAddress) route.proxy().address();
        String userInfo = findUserInfo(response.request().url().host(), proxyAddress.getHostName());
        if (userInfo == null || !userInfo.contains(":")) return null;
        int index = userInfo.indexOf(':');
        if (index <= 0 || index >= userInfo.length() - 1) return null;
        return response.request().newBuilder().header("Proxy-Authorization", Credentials.basic(userInfo.substring(0, index), userInfo.substring(index + 1))).build();
    }

    private String findUserInfo(String requestHost, String proxyHost) {
        if (selector == null || requestHost == null || proxyHost == null) return null;
        for (ProxyRule item : selector.getProxy()) {
            if (matchesHost(item, requestHost)) {
                String userInfo = item.getUserInfo(proxyHost);
                if (userInfo != null) return userInfo;
            }
        }
        return null;
    }

    private boolean matchesHost(ProxyRule item, String requestHost) {
        for (String host : item.getHosts()) {
            if (host != null && containOrMatch(requestHost, host)) return true;
        }
        return false;
    }

    private boolean containOrMatch(String text, String rule) {
        try {
            return text.contains(rule) || text.matches(rule);
        } catch (Throwable th) {
            return false;
        }
    }
}
