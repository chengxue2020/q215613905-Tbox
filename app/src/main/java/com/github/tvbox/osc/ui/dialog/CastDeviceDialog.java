package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.dlna.CastDevice;
import com.github.tvbox.osc.dlna.CastVideo;
import com.github.tvbox.osc.dlna.DLNACastManager;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

public class CastDeviceDialog extends BaseDialog {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, CastDevice> devices = new LinkedHashMap<>();
    private final CastVideo video;
    private final DeviceAdapter adapter = new DeviceAdapter();
    private OnCastListener onCastListener;
    private TextView title;
    private ProgressBar progressBar;
    private TextView tvScanning;
    private TextView tvEmpty;
    private boolean searchFinished;

    public CastDeviceDialog(@NonNull @NotNull Context context, CastVideo video) {
        super(context);
        this.video = video;
        setContentView(R.layout.dialog_cast);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = findViewById(R.id.title);
        progressBar = findViewById(R.id.progressBar);
        tvScanning = findViewById(R.id.tvScanning);
        tvEmpty = findViewById(R.id.tvEmpty);
        TextView btnRefresh = findViewById(R.id.btnRefresh);
        TextView btnCancel = findViewById(R.id.btnCancel);
        TvRecyclerView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        title.setText("投屏到设备");
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDevices();
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        searchDevices();
    }

    @Override
    public void dismiss() {
        DLNACastManager.get().setDeviceListener(null);
        DLNACastManager.get().release(getContext());
        super.dismiss();
    }

    public void setOnCastListener(OnCastListener listener) {
        this.onCastListener = listener;
    }

    private void searchDevices() {
        searchFinished = false;
        updateState();
        searchTvBoxDevices();
        searchDlnaDevices();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                searchFinished = true;
                updateState();
            }
        }, 15000);
    }

    private void refreshDevices() {
        handler.removeCallbacksAndMessages(null);
        DLNACastManager.get().setDeviceListener(null);
        DLNACastManager.get().release(getContext());
        devices.clear();
        adapter.setData(new ArrayList<CastDevice>());
        searchDevices();
    }

    private void searchTvBoxDevices() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RemoteTVBox tv = new RemoteTVBox();
                RemoteTVBox.searchAvalible(tv.new Callback() {
                    @Override
                    public void found(final String viewHost, boolean end) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                addDevice(CastDevice.tvbox(viewHost));
                            }
                        });
                    }

                    @Override
                    public void fail(boolean all, boolean end) {
                        if (end) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateTitle();
                                }
                            });
                        }
                    }
                });
            }
        }).start();
    }

    private void searchDlnaDevices() {
        DLNACastManager.get().setDeviceListener(new DLNACastManager.DeviceListener() {
            @Override
            public void onDeviceChanged() {
                for (CastDevice device : DLNACastManager.get().getDevices()) {
                    addDevice(device);
                }
            }
        });
        DLNACastManager.get().init(getContext());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                DLNACastManager.get().search();
            }
        }, 1000);
    }

    private void addDevice(CastDevice device) {
        devices.put(device.getType() + ":" + device.getId(), device);
        adapter.setData(new ArrayList<>(devices.values()));
        updateState();
    }

    private void updateTitle() {
        updateState();
    }

    private void updateState() {
        if (title == null) return;
        title.setText("投屏到设备");
        boolean hasDevice = !devices.isEmpty();
        if (progressBar != null) progressBar.setVisibility(!hasDevice && !searchFinished ? View.VISIBLE : View.GONE);
        if (tvScanning != null) tvScanning.setVisibility(!hasDevice && !searchFinished ? View.VISIBLE : View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(!hasDevice && searchFinished ? View.VISIBLE : View.GONE);
    }

    private void castToDevice(final CastDevice device) {
        if (device.getType() == CastDevice.TYPE_TVBOX) {
            castToTvBox(device);
            return;
        }
        DLNACastManager.get().cast(device, video, new DLNACastManager.CastCallback() {
            @Override
            public void onResult(boolean success, String msg) {
                handleCastResult(success, msg);
            }
        });
    }

    private void castToTvBox(CastDevice device) {
        try {
            String url = buildTvBoxUrl(video.getUrl(), video.getHeaders());
            Map<String, String> params = new HashMap<>();
            params.put("do", "push");
            params.put("url", url);
            RemoteTVBox.post("http://" + device.getId() + "/action", params, new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleCastResult(false, "TVBox投屏失败");
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final boolean ok;
                    try {
                        ok = response.body() != null && "ok".equals(response.body().string());
                    } finally {
                        response.close();
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleCastResult(ok, ok ? "" : "TVBox投屏失败");
                        }
                    });
                }
            });
        } catch (Exception e) {
            handleCastResult(false, "TVBox投屏失败");
        }
    }

    private String buildTvBoxUrl(String url, HashMap<String, String> headers) throws Exception {
        if (headers == null || headers.isEmpty()) return url;
        String json = new org.json.JSONObject(headers).toString();
        return url + "@Headers=" + URLEncoder.encode(json, "UTF-8") + "@";
    }

    private void handleCastResult(boolean success, String msg) {
        if (success) {
            Toast.makeText(getContext(), "投屏成功", Toast.LENGTH_SHORT).show();
            if (onCastListener != null) onCastListener.onCastSuccess();
            dismiss();
        } else {
            Toast.makeText(getContext(), msg == null || msg.length() == 0 ? "投屏失败" : msg, Toast.LENGTH_SHORT).show();
            if (onCastListener != null) onCastListener.onCastFailed();
        }
    }

    public interface OnCastListener {
        void onCastSuccess();

        void onCastFailed();
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceHolder> {
        private final List<CastDevice> data = new ArrayList<>();

        void setData(List<CastDevice> devices) {
            data.clear();
            data.addAll(devices);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DeviceHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cast_device, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceHolder holder, final int position) {
            final CastDevice device = data.get(position);
            TextView name = holder.itemView.findViewById(R.id.tvDeviceName);
            TextView ip = holder.itemView.findViewById(R.id.tvDeviceIp);
            name.setText(device.getName());
            ip.setText((device.getType() == CastDevice.TYPE_DLNA ? "DLNA  " : "TVBox  ") + device.getId());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    castToDevice(device);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class DeviceHolder extends RecyclerView.ViewHolder {
            DeviceHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
