package com.github.tvbox.osc.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.js.Trans;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DanmakuApi {
    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public interface SearchCallback {
        void onFound(String url);
    }

    public static boolean canSearch() {
        return DanmuHelper.isOpen() && !TextUtils.isEmpty(getApiUrl());
    }

    public static void search(String name, String episode, SearchCallback callback) {
        String apiUrl = getApiUrl();
        if (TextUtils.isEmpty(apiUrl) || callback == null) return;
        try {
            OkHttp.cancel(TAG);
            newCall(apiUrl, name, episode).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    LOG.e("echo-danmaku search error: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String body = response.body() == null ? "" : response.body().string();
                        String url = parseUrl(body);
                        if (!TextUtils.isEmpty(url)) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFound(url);
                                }
                            });
                        }
                    } catch (Throwable th) {
                        LOG.e("echo-danmaku search parse error: " + th.getMessage());
                    }
                }
            });
        } catch (Throwable th) {
            LOG.e("echo-danmaku search start error: " + th.getMessage());
        }
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }

    private static Call newCall(String apiUrl, String name, String episode) {
        name = Trans.t2s(name == null ? "" : name);
        episode = Trans.t2s(episode == null ? "" : episode);
        if (apiUrl.contains("{name}") || apiUrl.contains("{episode}")) {
            return OkHttp.newCall(apiUrl.replace("{name}", name).replace("{episode}", episode), TAG);
        }
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("name", name);
        params.put("episode", episode);
        return OkHttp.newCall(apiUrl, OkHttp.toBody(params), TAG);
    }

    private static String getApiUrl() {
        String custom = Hawk.get(HawkConfig.DANMU_API, "");
        if (!TextUtils.isEmpty(custom)) return custom.trim();
        return ApiConfig.get().getDanmaku().trim();
    }

    private static String parseUrl(String body) throws Exception {
        if (TextUtils.isEmpty(body)) return "";
        String text = body.trim();
        if (text.startsWith("[")) {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String url = object.optString("url", "").trim();
                if (!TextUtils.isEmpty(url)) return url;
            }
        } else if (text.startsWith("{")) {
            return new JSONObject(text).optString("url", "").trim();
        } else if (text.startsWith("http") || text.startsWith("file")) {
            return text;
        }
        return "";
    }
}
