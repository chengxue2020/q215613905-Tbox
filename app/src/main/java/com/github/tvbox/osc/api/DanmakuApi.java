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
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DanmakuApi {
    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final String BUILTIN_API = "https://saas-oa.shyeguang.cn";
    private static final String USE_DEFAULT_KEY = "danmu_api_use_default";
    private static final long BUILTIN_TIMEOUT = TimeUnit.SECONDS.toMillis(20);
    private static final int BUILTIN_MAX_RETRY = 2;
    private static final int EPISODE_QUERY_NUMBER = 0;
    private static final int EPISODE_QUERY_EMPTY = 1;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final AtomicInteger searchSeq = new AtomicInteger();

    public interface SearchCallback {
        void onFound(String url);

        default void onNotFound() {
        }
    }

    public static boolean canSearch() {
        return DanmuHelper.isOpen() && !TextUtils.isEmpty(getApiUrl());
    }

    public static String getDisplayApiUrl() {
        if (isUseDefault()) return "";
        String custom = Hawk.get(HawkConfig.DANMU_API, "");
        if (!TextUtils.isEmpty(custom)) return custom.trim();
        String config = ApiConfig.get().getDanmaku().trim();
        return TextUtils.isEmpty(config) ? "" : config;
    }

    public static boolean isUseDefault() {
        return Hawk.get(USE_DEFAULT_KEY, false);
    }

    public static void setUseDefault(boolean useDefault) {
        Hawk.put(USE_DEFAULT_KEY, useDefault);
        if (useDefault) Hawk.put(HawkConfig.DANMU_API, "");
    }

    public static void setCustomApi(String api) {
        Hawk.put(USE_DEFAULT_KEY, false);
        Hawk.put(HawkConfig.DANMU_API, api);
    }

    public static void search(String name, String episode, SearchCallback callback) {
        String apiUrl = getApiUrl();
//        LOG.i("echo-danmaku search apiUrl: " + apiUrl);
        if (TextUtils.isEmpty(apiUrl) || callback == null) return;
        try {
            OkHttp.cancel(TAG);
            int seq = searchSeq.incrementAndGet();
//            LOG.i("echo-danmaku search title: " + safeLog(name) + ", episode: " + safeLog(episode));
            if (!hasPlaceholder(apiUrl)) {
                searchBuiltin(apiUrl, name, episode, callback, 0, seq);
                return;
            }
            newCall(apiUrl, name, episode).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!isCurrentSearch(seq)) return;
                    LOG.e("echo-danmaku search error: " + e.getMessage());
                    notifyNotFound(callback, seq);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (!isCurrentSearch(seq)) return;
                    try {
                        String body = response.body() == null ? "" : response.body().string();
                        String url = parseUrl(body);
                        if (!TextUtils.isEmpty(url) && isCurrentSearch(seq)) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isCurrentSearch(seq)) callback.onFound(url);
                                }
                            });
                        } else {
                            notifyNotFound(callback, seq);
                        }
                    } catch (Throwable th) {
                        LOG.e("echo-danmaku search parse error: " + th.getMessage());
                        notifyNotFound(callback, seq);
                    }
                }
            });
        } catch (Throwable th) {
            LOG.e("echo-danmaku search start error: " + th.getMessage());
            notifyNotFound(callback, searchSeq.get());
        }
    }

    public static void cancel() {
        searchSeq.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    private static void searchBuiltin(String apiUrl, String name, String episode, SearchCallback callback, int retry, int seq) {
        searchBuiltin(apiUrl, name, episode, callback, retry, seq, EPISODE_QUERY_NUMBER);
    }

    private static void searchBuiltin(String apiUrl, String name, String episode, SearchCallback callback, int retry, int seq, int queryMode) {
        final String baseUrl = normalizeBaseUrl(apiUrl);
        final String simpleName = Trans.t2s(name == null ? "" : name);
        final String simpleEpisode = Trans.t2s(episode == null ? "" : episode);
        final String episodeQuery = getEpisodeQuery(simpleEpisode, queryMode);
        final String searchUrl = baseUrl + "/api/v2/search/episodes?anime=" + encode(simpleName)
                + (TextUtils.isEmpty(episodeQuery) ? "" : "&episode=" + encode(episodeQuery));
//        LOG.i("echo-danmaku builtin search episodes: " + searchUrl + ", retry=" + retry + ", mode=" + queryMode);
        OkHttp.newCall(OkHttp.client(BUILTIN_TIMEOUT), searchUrl, TAG).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isCurrentSearch(seq)) return;
                if (retry < BUILTIN_MAX_RETRY) {
                    LOG.e("echo-danmaku builtin search error: " + e.getMessage() + ", retry later");
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrentSearch(seq)) searchBuiltin(apiUrl, name, episode, callback, retry + 1, seq, queryMode);
                        }
                    }, 1500L * (retry + 1));
                } else {
                    LOG.e("echo-danmaku builtin search error: " + e.getMessage());
                    searchBuiltinAnime(baseUrl, simpleName, simpleEpisode, callback, seq, true);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!isCurrentSearch(seq)) return;
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    EpisodeMatch episodeMatch = findEpisodeFromSearchEpisodes(body, simpleEpisode);
                    if (episodeMatch != null && !TextUtils.isEmpty(episodeMatch.id)) {
                        loadBuiltinComment(baseUrl, simpleName, simpleEpisode, episodeMatch, callback, seq);
                        return;
                    }
                    if (isSearchEpisodesMovieResult(body)) {
                        LOG.i("echo-danmaku builtin movie result not matched, skip anime fallback");
                        notifyNotFound(callback, seq);
                        return;
                    }
                    if (tryNextEpisodeQuery(apiUrl, name, episode, callback, seq, queryMode)) return;
                    LOG.i("echo-danmaku builtin episode not matched, title: " + safeLog(simpleName) + ", episode: " + safeLog(simpleEpisode));
                    searchBuiltinAnime(baseUrl, simpleName, simpleEpisode, callback, seq, true);
                } catch (Throwable th) {
                    LOG.e("echo-danmaku builtin episode parse error: " + th.getMessage());
                    if (tryNextEpisodeQuery(apiUrl, name, episode, callback, seq, queryMode)) return;
                    searchBuiltinAnime(baseUrl, simpleName, simpleEpisode, callback, seq, true);
                }
            }
        });
    }

    private static boolean tryNextEpisodeQuery(String apiUrl, String name, String episode, SearchCallback callback, int seq, int queryMode) {
        int nextMode = getNextEpisodeQueryMode(Trans.t2s(episode == null ? "" : episode), queryMode);
        if (nextMode < 0) return false;
//        LOG.i("echo-danmaku builtin retry episodes query mode: " + queryMode + " -> " + nextMode);
        searchBuiltin(apiUrl, name, episode, callback, 0, seq, nextMode);
        return true;
    }

    private static void searchBuiltinAnime(String baseUrl, String name, String episode, SearchCallback callback, int seq, boolean notifyOnEmpty) {
        final String searchUrl = baseUrl + "/api/v2/search/anime?keyword=" + encode(name);
        LOG.i("echo-danmaku builtin search anime: " + searchUrl);
        OkHttp.newCall(OkHttp.client(BUILTIN_TIMEOUT), searchUrl, TAG).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isCurrentSearch(seq)) return;
                LOG.e("echo-danmaku builtin anime error: " + e.getMessage());
                if (notifyOnEmpty) notifyNotFound(callback, seq);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!isCurrentSearch(seq)) return;
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    String animeId = findAnimeId(body);
                    if (TextUtils.isEmpty(animeId)) {
                        if (notifyOnEmpty) notifyNotFound(callback, seq);
                        return;
                    }
                    loadBuiltinBangumi(baseUrl, animeId, episode, callback, seq);
                } catch (Throwable th) {
                    LOG.e("echo-danmaku builtin anime parse error: " + th.getMessage());
                    if (notifyOnEmpty) notifyNotFound(callback, seq);
                }
            }
        });
    }

    private static void loadBuiltinBangumi(String baseUrl, String animeId, String episode, SearchCallback callback, int seq) {
        final String bangumiUrl = baseUrl + "/api/v2/bangumi/" + animeId;
//        LOG.i("echo-danmaku builtin bangumi: " + bangumiUrl);
        OkHttp.newCall(OkHttp.client(BUILTIN_TIMEOUT), bangumiUrl, TAG).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isCurrentSearch(seq)) return;
                LOG.e("echo-danmaku builtin bangumi error: " + e.getMessage());
                notifyNotFound(callback, seq);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!isCurrentSearch(seq)) return;
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    EpisodeMatch episodeMatch = findEpisode(body, episode);
                    if (episodeMatch != null && !TextUtils.isEmpty(episodeMatch.id)) {
                        loadBuiltinComment(baseUrl, "", episode, episodeMatch, callback, seq);
                    } else {
//                        LOG.i("echo-danmaku builtin bangumi episode not matched, episode: " + safeLog(episode));
                        notifyNotFound(callback, seq);
                    }
                } catch (Throwable th) {
                    LOG.e("echo-danmaku builtin bangumi parse error: " + th.getMessage());
                    notifyNotFound(callback, seq);
                }
            }
        });
    }

    private static void loadBuiltinComment(String baseUrl, String title, String episode, EpisodeMatch episodeMatch, SearchCallback callback, int seq) {
        final String commentUrl = baseUrl + "/api/v2/comment/" + episodeMatch.id + "?format=json";
        LOG.i("echo-danmaku builtin load title: " + safeLog(title)
                + ", request episode: " + safeLog(episode)
                + ", matched episode: " + safeLog(episodeMatch.title)
                + ", matched number: " + episodeMatch.number
                + ", episodeId: " + episodeMatch.id);
        LOG.i("echo-danmaku builtin comment: " + commentUrl);
        OkHttp.newCall(OkHttp.client(BUILTIN_TIMEOUT), commentUrl, TAG).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!isCurrentSearch(seq)) return;
                LOG.e("echo-danmaku builtin comment error: " + e.getMessage());
                notifyNotFound(callback, seq);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!isCurrentSearch(seq)) return;
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    final String xml = commentJsonToXml(body);
                    if (TextUtils.isEmpty(xml) || !isCurrentSearch(seq)) {
                        notifyNotFound(callback, seq);
                        return;
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrentSearch(seq)) callback.onFound(xml);
                        }
                    });
                } catch (Throwable th) {
                    LOG.e("echo-danmaku builtin comment parse error: " + th.getMessage());
                    notifyNotFound(callback, seq);
                }
            }
        });
    }

    private static void notifyNotFound(SearchCallback callback, int seq) {
        if (callback == null || !isCurrentSearch(seq)) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isCurrentSearch(seq)) callback.onNotFound();
            }
        });
    }

    private static boolean isCurrentSearch(int seq) {
        return seq == searchSeq.get();
    }

    private static Call newCall(String apiUrl, String name, String episode) {
        name = Trans.t2s(name == null ? "" : name);
        episode = Trans.t2s(episode == null ? "" : episode);
        if (hasPlaceholder(apiUrl)) {
            return OkHttp.newCall(apiUrl.replace("{name}", name).replace("{episode}", episode), TAG);
        }
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("name", name);
        params.put("episode", episode);
        return OkHttp.newCall(apiUrl, OkHttp.toBody(params), TAG);
    }

    private static String getApiUrl() {
        if (isUseDefault()) return BUILTIN_API;
        String custom = Hawk.get(HawkConfig.DANMU_API, "");
        if (!TextUtils.isEmpty(custom)) return custom.trim();
        String config = ApiConfig.get().getDanmaku().trim();
        if (!TextUtils.isEmpty(config)) return config;
        return BUILTIN_API;
    }

    private static boolean hasPlaceholder(String apiUrl) {
        return !TextUtils.isEmpty(apiUrl) && (apiUrl.contains("{name}") || apiUrl.contains("{episode}"));
    }

    private static String normalizeBaseUrl(String apiUrl) {
        String url = apiUrl == null ? "" : apiUrl.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/87654321")) url = url.substring(0, url.length() - "/87654321".length());
        return url;
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text == null ? "" : text, "UTF-8");
        } catch (Throwable th) {
            return text == null ? "" : text;
        }
    }

    private static String getEpisodeQuery(String episode, int queryMode) {
        if (queryMode == EPISODE_QUERY_EMPTY) return "";
        int number = extractNumber(episode);
        return number > 0 ? String.valueOf(number) : "";
    }

    private static int getNextEpisodeQueryMode(String episode, int queryMode) {
        if (queryMode == EPISODE_QUERY_NUMBER && !TextUtils.isEmpty(getEpisodeQuery(episode, EPISODE_QUERY_NUMBER))) {
            return EPISODE_QUERY_EMPTY;
        }
        return -1;
    }

    private static String findAnimeId(String body) throws Exception {
        JSONObject object = new JSONObject(body);
        JSONArray array = object.optJSONArray("animes");
        if (array == null) array = object.optJSONArray("anime");
        if (array == null) array = object.optJSONArray("data");
        if (array == null || array.length() <= 0) return "";
        JSONObject item = array.optJSONObject(0);
        if (item == null) return "";
        String id = item.optString("animeId", "");
        if (TextUtils.isEmpty(id)) id = item.optString("id", "");
        return id;
    }

    private static EpisodeMatch findEpisode(String body, String episode) throws Exception {
        return findEpisode(body, episode, true);
    }

    private static EpisodeMatch findEpisode(String body, String episode, boolean allowMovieFallback) throws Exception {
        JSONObject object = new JSONObject(body);
        EpisodeList episodeList = findEpisodeList(object);
        JSONArray episodes = episodeList == null ? null : episodeList.episodes;
        if (episodes == null || episodes.length() <= 0) return null;
        int targetNumber = extractNumber(episode);
        EpisodeMatch first = null;
        EpisodeMatch firstMandarin = null;
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null) continue;
            String id = firstString(item, "episodeId", "id");
            if (TextUtils.isEmpty(id)) continue;
            String title = firstString(item, "episodeTitle", "title", "name");
            int number = parseEpisodeNumber(firstString(item, "episodeNumber", "number", "sort"));
            EpisodeMatch match = new EpisodeMatch(id, title, number);
            if (first == null) first = match;
            if (firstMandarin == null && isMandarinTitle(title)) firstMandarin = match;
            if (!TextUtils.isEmpty(episode) && !TextUtils.isEmpty(title) && title.contains(episode)) {
                return match;
            }
            if (targetNumber > 0 && number == targetNumber) return match;
            if (targetNumber > 0 && extractNumber(title) == targetNumber) return match;
        }
        if (TextUtils.isEmpty(episode)) return first;
        if (allowMovieFallback && episodeList.isMovie) return firstMandarin == null ? first : firstMandarin;
        return null;
    }

    private static EpisodeMatch findEpisodeFromSearchEpisodes(String body, String episode) throws Exception {
        EpisodeMatch match = findEpisode(body, episode, false);
        if (match != null) return match;
        EpisodeMatch movieFallback = findMovieFallback(new JSONObject(body), episode);
        if (movieFallback != null) {
            LOG.i("echo-danmaku episodes movie fallback episode: " + safeLog(movieFallback.title) + ", episodeId: " + movieFallback.id);
        }
        return movieFallback;
    }

    private static JSONArray findEpisodes(JSONObject object) {
        EpisodeList episodeList = findEpisodeList(object);
        return episodeList == null ? null : episodeList.episodes;
    }

    private static EpisodeList findEpisodeList(JSONObject object) {
        JSONArray array = object.optJSONArray("episodes");
        if (array != null) return new EpisodeList(array, isMovieType(object));
        JSONObject bangumi = object.optJSONObject("bangumi");
        if (bangumi != null) {
            array = bangumi.optJSONArray("episodes");
            if (array != null) return new EpisodeList(array, isMovieType(bangumi));
        }
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) animes = object.optJSONArray("anime");
        if (animes == null) animes = object.optJSONArray("data");
        if (animes != null) {
            for (int i = 0; i < animes.length(); i++) {
                JSONObject item = animes.optJSONObject(i);
                if (item == null) continue;
                array = item.optJSONArray("episodes");
                if (array != null && array.length() > 0) return new EpisodeList(array, isMovieType(item));
            }
        }
        return null;
    }

    private static boolean isMovieType(JSONObject object) {
        if (object == null) return false;
        if (isMovieTypeText(object.optString("type", ""))) return true;
        if (isMovieTypeText(object.optString("typeDescription", ""))) return true;
        JSONObject bangumi = object.optJSONObject("bangumi");
        if (bangumi != null && isMovieTypeText(bangumi.optString("type", ""))) return true;
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) animes = object.optJSONArray("anime");
        if (animes == null) animes = object.optJSONArray("data");
        if (animes == null) return false;
        for (int i = 0; i < animes.length(); i++) {
            JSONObject item = animes.optJSONObject(i);
            if (item != null && isMovieTypeText(item.optString("type", ""))) return true;
        }
        return false;
    }

    private static boolean isMovieTypeText(String type) {
        return "\u7535\u5f71".equals(type);
    }

    private static boolean isMandarinTitle(String title) {
        return !TextUtils.isEmpty(title) && (title.contains("\u56fd\u8bed") || title.contains("\u666e\u901a\u8bdd"));
    }

    private static boolean isCantoneseTitle(String title) {
        return !TextUtils.isEmpty(title) && title.contains("\u7ca4\u8bed");
    }

    private static boolean prefersCantonese(String episode) {
        return isCantoneseTitle(episode);
    }

    private static boolean isPreferredLanguageTitle(String title, boolean preferCantonese) {
        return preferCantonese ? isCantoneseTitle(title) : isMandarinTitle(title);
    }

    private static boolean isSearchEpisodesMovieResult(String body) {
        try {
            JSONObject object = new JSONObject(body);
            JSONArray animes = object.optJSONArray("animes");
            if (animes == null) animes = object.optJSONArray("anime");
            if (animes == null) animes = object.optJSONArray("data");
            if (animes == null) return false;
            for (int i = 0; i < animes.length(); i++) {
                JSONObject anime = animes.optJSONObject(i);
                if (anime != null && isMovieType(anime) && anime.optJSONArray("episodes") != null) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static EpisodeMatch findMovieFallback(JSONObject object, String episode) {
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) animes = object.optJSONArray("anime");
        if (animes == null) animes = object.optJSONArray("data");
        if (animes == null) return null;
        boolean preferCantonese = prefersCantonese(episode);
        EpisodeMatch firstMovie = null;
        for (int i = 0; i < animes.length(); i++) {
            JSONObject anime = animes.optJSONObject(i);
            if (anime == null || !isMovieType(anime)) continue;
            JSONArray episodes = anime.optJSONArray("episodes");
            if (episodes == null) continue;
            for (int j = 0; j < episodes.length(); j++) {
                JSONObject item = episodes.optJSONObject(j);
                if (item == null) continue;
                String id = firstString(item, "episodeId", "id");
                if (TextUtils.isEmpty(id)) continue;
                String title = firstString(item, "episodeTitle", "title", "name");
                EpisodeMatch match = new EpisodeMatch(id, title, parseEpisodeNumber(firstString(item, "episodeNumber", "number", "sort")));
                if (firstMovie == null) firstMovie = match;
                if (isPreferredLanguageTitle(title, preferCantonese)) return match;
            }
        }
        return firstMovie;
    }

    private static int parseEpisodeNumber(String value) {
        try {
            if (TextUtils.isEmpty(value)) return -1;
            return (int) Float.parseFloat(value);
        } catch (Throwable th) {
            return extractNumber(value);
        }
    }

    private static int extractNumber(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) builder.append(ch);
        }
        if (builder.length() == 0) return -1;
        try {
            return Integer.parseInt(builder.toString());
        } catch (Throwable th) {
            return -1;
        }
    }

    private static String commentJsonToXml(String body) throws Exception {
        JSONObject object = new JSONObject(body);
        JSONArray comments = object.optJSONArray("comments");
        if (comments == null) comments = object.optJSONArray("data");
        if (comments == null || comments.length() <= 0) return "";
        StringBuilder builder = new StringBuilder();
        builder.append("<i>");
        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            String param = item.optString("p", "");
            String text = item.optString("m", "");
            if (TextUtils.isEmpty(text)) text = item.optString("text", "");
            if (TextUtils.isEmpty(param) || TextUtils.isEmpty(text)) continue;
            builder.append("<d p=\"").append(escapeXml(normalizeDanmakuParam(param))).append("\">")
                    .append(escapeXml(text)).append("</d>");
        }
        builder.append("</i>");
        String xml = builder.toString();
        LOG.i("echo-danmaku builtin xml length: " + xml.length());
        return xml;
    }

    private static String normalizeDanmakuParam(String param) {
        String[] values = param.split(",");
        if (values.length < 4) return param;
        String time = values[0];
        String type = values[1];
        String size = values[2];
        String color = values[3];
        if (isColorValue(size)) {
            color = size;
            size = "25";
        } else if (!isColorValue(color)) {
            color = "16777215";
        }
        return time + "," + type + "," + size + "," + normalizeColor(color);
    }

    private static boolean isColorValue(String value) {
        try {
            if (TextUtils.isEmpty(value)) return false;
            String text = value.trim();
            if (text.startsWith("#")) return true;
            if (text.startsWith("0x") || text.startsWith("0X")) return true;
            long color = Long.parseLong(text);
            return color >= 0 && color <= 0x00ffffffL;
        } catch (Throwable th) {
            return false;
        }
    }

    private static String normalizeColor(String color) {
        if (TextUtils.isEmpty(color)) return "16777215";
        String text = color.trim();
        try {
            if (text.startsWith("#")) return String.valueOf(Long.parseLong(text.substring(1), 16));
            if (text.startsWith("0x") || text.startsWith("0X")) return String.valueOf(Long.parseLong(text.substring(2), 16));
        } catch (Throwable ignored) {
        }
        return text;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String safeLog(String text) {
        return TextUtils.isEmpty(text) ? "" : text;
    }

    private static class EpisodeList {
        final JSONArray episodes;
        final boolean isMovie;

        EpisodeList(JSONArray episodes, boolean isMovie) {
            this.episodes = episodes;
            this.isMovie = isMovie;
        }
    }

    private static class EpisodeMatch {
        final String id;
        final String title;
        final int number;

        EpisodeMatch(String id, String title, int number) {
            this.id = id;
            this.title = title;
            this.number = number;
        }
    }

    private static String escapeXml(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace(">", "&gt;")
                .replace("<", "&lt;");
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
