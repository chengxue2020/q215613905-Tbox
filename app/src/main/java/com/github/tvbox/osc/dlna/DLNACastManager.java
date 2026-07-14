package com.github.tvbox.osc.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.gson.Gson;
import com.github.tvbox.osc.util.LOG;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.Seek;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.model.SeekMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DLNACastManager extends DefaultRegistryListener implements ServiceConnection {
    private static final UDADeviceType RENDERER_TYPE = new UDADeviceType("MediaRenderer", 1);
    private static final UDAServiceType AVT_TYPE = new UDAServiceType("AVTransport", 1);
    private static final DLNACastManager INSTANCE = new DLNACastManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, CastDevice> devices = new LinkedHashMap<>();
    private AndroidUpnpService upnpService;
    private DeviceListener deviceListener;
    private WifiManager.MulticastLock multicastLock;
    private boolean binding;

    public static DLNACastManager get() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (upnpService != null || binding) {
            search();
            return;
        }
        Context appContext = context.getApplicationContext();
        acquireMulticastLock(appContext);
        binding = appContext.bindService(new Intent(context, DLNACastService.class), this, Context.BIND_AUTO_CREATE);
        if (!binding) releaseMulticastLock();
    }

    public void release(Context context) {
        try {
            if (upnpService != null) upnpService.getRegistry().removeListener(this);
            context.getApplicationContext().unbindService(this);
        } catch (Exception ignored) {
        }
        upnpService = null;
        binding = false;
        devices.clear();
        releaseMulticastLock();
    }

    private void acquireMulticastLock(Context context) {
        try {
            if (multicastLock != null && multicastLock.isHeld()) return;
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return;
            multicastLock = wifiManager.createMulticastLock("tvbox_dlna_cast");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {
        }
        multicastLock = null;
    }

    public void setDeviceListener(DeviceListener listener) {
        this.deviceListener = listener;
    }

    public void search() {
        if (upnpService != null) {
            upnpService.getControlPoint().search(new STAllHeader());
            loadRegisteredDevices();
        }
    }

    public List<CastDevice> getDevices() {
        return new ArrayList<>(devices.values());
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binding = false;
        upnpService = (AndroidUpnpService) service;
        upnpService.getRegistry().addListener(this);
        search();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        upnpService = null;
        binding = false;
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) addDevice(CastDevice.dlna(device));
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) removeDevice(CastDevice.dlna(device));
    }

    private void loadRegisteredDevices() {
        if (upnpService == null) return;
        for (org.fourthline.cling.model.meta.Device device : upnpService.getRegistry().getDevices(RENDERER_TYPE)) {
            if (device instanceof RemoteDevice) addDevice(CastDevice.dlna((RemoteDevice) device));
        }
    }

    private void addDevice(final CastDevice device) {
        devices.put(device.getId(), device);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (deviceListener != null) deviceListener.onDeviceChanged();
            }
        });
    }

    private void removeDevice(final CastDevice device) {
        devices.remove(device.getId());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (deviceListener != null) deviceListener.onDeviceChanged();
            }
        });
    }

    public void cast(final CastDevice device, final CastVideo video, final CastCallback callback) {
        ControlPoint control = upnpService == null ? null : upnpService.getControlPoint();
        RemoteService service = findAVTransport(device);
        if (control == null || service == null) {
            postFail(callback, "设备离线");
            return;
        }
        LOG.i("dlna-cast start device=" + device.getName() + ", id=" + device.getId() + ", url=" + video.getUrl());
        control.execute(uriAction(control, service, video, callback));
    }

    private RemoteService findAVTransport(CastDevice device) {
        if (upnpService == null || device == null) return null;
        for (org.fourthline.cling.model.meta.Device item : upnpService.getRegistry().getDevices(RENDERER_TYPE)) {
            if (!(item instanceof RemoteDevice)) continue;
            RemoteDevice remote = (RemoteDevice) item;
            if (remote.getIdentity().getUdn().getIdentifierString().equals(device.getId())) {
                return remote.findService(AVT_TYPE);
            }
        }
        return null;
    }

    private SetAVTransportURI uriAction(final ControlPoint control, final RemoteService service, final CastVideo video, final CastCallback callback) {
        String metaData = buildMetaData(video);
        return new SetAVTransportURI(service, video.getUrl(), metaData) {
            @Override
            public void success(ActionInvocation invocation) {
                control.execute(playAction(control, service, video, callback));
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LOG.e("dlna-cast SetAVTransportURI failure: " + formatResponse(operation, defaultMsg));
                postFail(callback, defaultMsg);
            }
        };
    }

    private Play playAction(final ControlPoint control, final RemoteService service, final CastVideo video, final CastCallback callback) {
        return new Play(service) {
            @Override
            public void success(ActionInvocation invocation) {
                if (video.getPosition() > 0) control.execute(seekAction(service, video.getPosition()));
                postSuccess(callback);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LOG.e("dlna-cast Play failure: " + formatResponse(operation, defaultMsg));
                postFail(callback, defaultMsg);
            }
        };
    }

    private Seek seekAction(RemoteService service, long position) {
        return new Seek(service, SeekMode.REL_TIME, formatMs(position)) {
            @Override
            public void success(ActionInvocation invocation) {
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LOG.i("dlna-cast Seek ignored: " + formatResponse(operation, defaultMsg));
            }
        };
    }

    private String buildMetaData(CastVideo video) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ");
            sb.append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ");
            sb.append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">");
            sb.append("<item id=\"0\" parentID=\"-1\" restricted=\"0\">");
            sb.append("<dc:title>").append(escapeXml(video.getName())).append("</dc:title>");
            sb.append("<dc:creator></dc:creator>");
            sb.append("<upnp:class>object.item.videoItem</upnp:class>");
            HashMap<String, String> headers = video.getHeaders();
            if (headers != null && !headers.isEmpty()) {
                sb.append("<dc:description>").append(escapeXml(new Gson().toJson(headers))).append("</dc:description>");
            }
            sb.append("<res protocolInfo=\"http-get:*:video/*:*\">").append(escapeXml(video.getUrl())).append("</res>");
            sb.append("</item>");
            sb.append("</DIDL-Lite>");
            return sb.toString();
        } catch (Exception e) {
            LOG.e("dlna-cast metadata failure: " + e.getMessage());
            return "";
        }
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }

    private String formatMs(long ms) {
        if (ms <= 0) return "00:00:00";
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private String formatResponse(UpnpResponse operation, String defaultMsg) {
        if (operation == null) return defaultMsg == null ? "" : defaultMsg;
        return operation.getStatusCode() + " " + operation.getStatusMessage() + " " + (defaultMsg == null ? "" : defaultMsg);
    }

    private void postSuccess(final CastCallback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onResult(true, "");
            }
        });
    }

    private void postFail(final CastCallback callback, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onResult(false, msg == null ? "投屏失败" : msg);
            }
        });
    }

    public interface DeviceListener {
        void onDeviceChanged();
    }

    public interface CastCallback {
        void onResult(boolean success, String msg);
    }
}
