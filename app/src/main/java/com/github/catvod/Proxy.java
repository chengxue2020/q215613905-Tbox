package com.github.catvod;

import com.github.tvbox.osc.server.RemoteServer;

public class Proxy {

    private static int port = RemoteServer.serverPort;

    public static void set(int port) {
        Proxy.port = port;
    }

    public static int getPort() {
        return port > 0 ? port : RemoteServer.serverPort;
    }

    public static String getUrl(boolean local) {
        return "http://" + (local ? "127.0.0.1" : getIp()) + ":" + getPort() + "/proxy";
    }

    private static String getIp() {
        try {
            return RemoteServer.getLocalIPAddress(com.github.tvbox.osc.base.App.getInstance());
        } catch (Throwable th) {
            return "127.0.0.1";
        }
    }
}
