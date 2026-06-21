package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.util.OkGoHelper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Dns;

public class OkDns implements Dns {

    private final ConcurrentHashMap<String, String> hosts = new ConcurrentHashMap<>();

    public void addAll(List<String> hosts) {
        if (hosts == null) return;
        for (String host : hosts) {
            if (host == null) continue;
            String[] splits = host.split("=", 2);
            if (splits.length == 2) this.hosts.put(splits[0].trim(), splits[1].trim());
        }
    }

    public void clear() {
        hosts.clear();
    }

    private String get(String hostname) {
        String target = hosts.get(hostname);
        if (target != null) return target;
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            if (hostname.contains(entry.getKey())) return entry.getValue();
        }
        return hostname;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        Dns dns = OkGoHelper.dnsOverHttps != null ? OkGoHelper.dnsOverHttps : Dns.SYSTEM;
        return dns.lookup(get(hostname));
    }
}
