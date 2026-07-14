package com.github.tvbox.osc.dlna;

import java.util.HashMap;

public class CastVideo {
    private final String url;
    private final String name;
    private final HashMap<String, String> headers;
    private final long position;

    public CastVideo(String url, String name, HashMap<String, String> headers, long position) {
        this.url = url;
        this.name = name;
        this.headers = headers == null ? new HashMap<String, String>() : headers;
        this.position = position;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    public long getPosition() {
        return position;
    }
}
