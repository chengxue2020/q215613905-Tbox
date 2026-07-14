package com.github.tvbox.osc.dlna;

import android.text.TextUtils;

import org.fourthline.cling.model.meta.RemoteDevice;

public class CastDevice {
    public static final int TYPE_TVBOX = 1;
    public static final int TYPE_DLNA = 2;

    private final int type;
    private final String id;
    private final String name;

    public static CastDevice tvbox(String host) {
        return new CastDevice(TYPE_TVBOX, host, "TVBox " + host);
    }

    public static CastDevice dlna(RemoteDevice device) {
        String name = device.getDetails() == null ? "DLNA" : device.getDetails().getFriendlyName();
        String uuid = device.getIdentity().getUdn().getIdentifierString();
        return new CastDevice(TYPE_DLNA, uuid, TextUtils.isEmpty(name) ? "DLNA" : name);
    }

    public CastDevice(int type, String id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return (type == TYPE_DLNA ? "DLNA  " : "TVBox  ") + name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CastDevice)) return false;
        CastDevice other = (CastDevice) obj;
        return type == other.type && TextUtils.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return (type * 31) + (id == null ? 0 : id.hashCode());
    }
}
