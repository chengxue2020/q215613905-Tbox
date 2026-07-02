package com.github.tvbox.osc.util.live;

import com.github.tvbox.osc.util.DefaultConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {
    public static final String DEFAULT_GROUP_NAME = "直播";
    private static final String LEGACY_DEFAULT_GROUP_NAME = "Ungrouped";
    private static final Pattern NAME_PATTERN = Pattern.compile(".*,(.+?)$");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"(.*?)\"");
    private static final Pattern TVG_CHNO_PATTERN = Pattern.compile("tvg-chno=\"(.*?)\"");
    private static final Pattern TVG_LOGO_PATTERN = Pattern.compile("tvg-logo=\"(.*?)\"");
    private static final Pattern TVG_NAME_PATTERN = Pattern.compile("tvg-name=\"(.*?)\"");
    private static final Pattern TVG_URL_PATTERN = Pattern.compile("tvg-url=\"(.*?)\"");
    private static final Pattern TVG_ID_PATTERN = Pattern.compile("tvg-id=\"(.*?)\"");
    private static final Pattern HTTP_USER_AGENT_PATTERN = Pattern.compile("http-user-agent=\"(.*?)\"");
    private static final Pattern CATCHUP_PATTERN = Pattern.compile("catchup=\"(.*?)\"");
    private static final Pattern CATCHUP_SOURCE_PATTERN = Pattern.compile("catchup-source=\"(.*?)\"");
    private static final Pattern CATCHUP_REPLACE_PATTERN = Pattern.compile("catchup-replace=\"(.*?)\"");

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        linkedHashMap.clear();
        JsonArray array = parseToJsonArray(str);
        if (array == null) return;
        for (JsonElement groupElement : array) {
            JsonObject groupObj = groupElement.getAsJsonObject();
            String groupName = normalizeGroupName(DefaultConfig.safeJsonString(groupObj, "group", DEFAULT_GROUP_NAME));
            LinkedHashMap<String, ArrayList<String>> channelMap = new LinkedHashMap<>();
            if (groupObj.has("channels")) {
                for (JsonElement channelElement : groupObj.getAsJsonArray("channels")) {
                    JsonObject channelObj = channelElement.getAsJsonObject();
                    String channelName = DefaultConfig.safeJsonString(channelObj, "name", "Unnamed");
                    ArrayList<String> urls = new ArrayList<String>();
                    if (channelObj.has("urls")) {
                        for (JsonElement urlElement : channelObj.getAsJsonArray("urls")) {
                            String url = urlElement.getAsString().trim();
                            if (isUrl(url) && !urls.contains(url)) urls.add(url);
                        }
                    }
                    if (!urls.isEmpty()) channelMap.put(channelName, urls);
                }
            }
            if (!channelMap.isEmpty()) linkedHashMap.put(groupName, channelMap);
        }
    }

    public static JsonArray parseToJsonArray(String str) {
        if (str == null) return new JsonArray();
        str = str.trim();
        if (str.isEmpty()) return new JsonArray();
        try {
            JsonElement element = JsonParser.parseString(str);
            if (element.isJsonArray()) return normalizeJsonArray(element.getAsJsonArray());
        } catch (Throwable ignored) {
        }
        if (str.startsWith("#EXTM3U")) return parseM3uToJsonArray(str);
        return parseTxtToJsonArray(str);
    }

    private static JsonArray normalizeJsonArray(JsonArray groups) {
        JsonArray result = new JsonArray();
        for (JsonElement groupElement : groups) {
            JsonObject groupObj = groupElement.getAsJsonObject();
            JsonObject outGroup = new JsonObject();
            String groupName = DefaultConfig.safeJsonString(groupObj, "group", "");
            if (groupName.isEmpty()) groupName = DefaultConfig.safeJsonString(groupObj, "name", DEFAULT_GROUP_NAME);
            outGroup.addProperty("group", normalizeGroupName(groupName));
            JsonArray channels = null;
            if (groupObj.has("channels") && groupObj.get("channels").isJsonArray()) {
                channels = groupObj.getAsJsonArray("channels");
            } else if (groupObj.has("channel") && groupObj.get("channel").isJsonArray()) {
                channels = groupObj.getAsJsonArray("channel");
            }
            if (channels != null) {
                for (JsonElement channelElement : channels) {
                    JsonObject channelObj = channelElement.getAsJsonObject();
                    JsonObject outChannel = new JsonObject();
                    copyIfExists(channelObj, outChannel, "name");
                    copyIfExists(channelObj, outChannel, "urls");
                    copyIfExists(channelObj, outChannel, "logo");
                    copyIfExists(channelObj, outChannel, "epg");
                    copyIfExists(channelObj, outChannel, "ua");
                    copyIfExists(channelObj, outChannel, "click");
                    copyIfExists(channelObj, outChannel, "format");
                    copyIfExists(channelObj, outChannel, "origin");
                    copyIfExists(channelObj, outChannel, "referer");
                    copyIfExists(channelObj, outChannel, "tvg-id");
                    copyIfExists(channelObj, outChannel, "tvg-name");
                    copyIfExists(channelObj, outChannel, "tvg-chno");
                    copyIfExists(channelObj, outChannel, "parse");
                    copyIfExists(channelObj, outChannel, "header");
                    copyIfExists(channelObj, outChannel, "catchup");
                    copyIfExists(channelObj, outChannel, "catchup-source");
                    copyIfExists(channelObj, outChannel, "catchup-replace");
                    addChannel(outGroup, outChannel);
                }
            }
            if (!outGroup.has("channels")) outGroup.add("channels", new JsonArray());
            result.add(outGroup);
        }
        return result;
    }

    private static void copyIfExists(JsonObject src, JsonObject dst, String key) {
        if (src.has(key)) dst.add(key, src.get(key));
    }

    private static JsonArray parseM3uToJsonArray(String str) {
        JsonArray result = new JsonArray();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(str.replace("\r\n", "\n").replace("\r", "")));
            String line;
            JsonObject currentGroup = null;
            JsonObject pendingChannel = null;
            JsonObject pendingMeta = new JsonObject();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTM3U")) {
                    mergeMeta(pendingMeta, buildMeta(line));
                    continue;
                }
                if (isSetting(line)) {
                    mergeMeta(pendingMeta, buildSetting(line));
                    continue;
                }
                if (line.startsWith("#EXTINF") || line.contains("#EXTINF")) {
                    String groupName = get(line, GROUP_PATTERN);
                    groupName = normalizeGroupName(groupName);
                    currentGroup = findOrCreateGroup(result, groupName);
                    pendingChannel = new JsonObject();
                    pendingChannel.addProperty("name", get(line, NAME_PATTERN));
                    mergeMeta(pendingChannel, buildMeta(line));
                    mergeMeta(pendingChannel, pendingMeta);
                    pendingMeta = new JsonObject();
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (currentGroup == null) currentGroup = findOrCreateGroup(result, DEFAULT_GROUP_NAME);
                if (pendingChannel == null) pendingChannel = new JsonObject();
                String[] parts = line.split("\\|", 2);
                String url = parts[0].trim();
                if (!isUrl(url)) continue;
                if (parts.length > 1) mergeMeta(pendingMeta, parseHeaderString(parts[1]));
                mergeMeta(pendingChannel, pendingMeta);
                JsonArray urls = pendingChannel.has("urls") ? pendingChannel.getAsJsonArray("urls") : new JsonArray();
                if (!containsUrl(urls, url)) urls.add(url);
                pendingChannel.add("urls", urls);
                addChannel(currentGroup, pendingChannel);
                pendingChannel = null;
                pendingMeta = new JsonObject();
            }
            reader.close();
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static JsonArray parseTxtToJsonArray(String str) {
        JsonArray result = new JsonArray();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(str.replace("\r\n", "\n").replace("\r", "")));
            String line;
            JsonObject currentGroup = null;
            JsonObject pendingMeta = new JsonObject();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    if (isSetting(line)) mergeMeta(pendingMeta, buildSetting(line));
                    continue;
                }
                if (line.contains("#genre#")) {
                    String groupName = line.split(",", 2)[0].trim();
                    currentGroup = findOrCreateGroup(result, groupName);
                    pendingMeta = new JsonObject();
                    continue;
                }
                String[] split = line.split(",", 2);
                if (split.length < 2) continue;
                if (currentGroup == null) currentGroup = findOrCreateGroup(result, DEFAULT_GROUP_NAME);
                JsonObject channel = new JsonObject();
                channel.addProperty("name", split[0].trim());
                mergeMeta(channel, pendingMeta);
                ArrayList<String> urls = new ArrayList<String>();
                for (String part : split[1].trim().split("#")) {
                    String url = part.trim();
                    if (isUrl(url) && !urls.contains(url)) urls.add(url);
                }
                if (urls.isEmpty()) continue;
                JsonArray urlArray = new JsonArray();
                for (String url : urls) urlArray.add(url);
                channel.add("urls", urlArray);
                addChannel(currentGroup, channel);
                pendingMeta = new JsonObject();
            }
            reader.close();
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static JsonObject parseHeaderString(String text) {
        JsonObject wrapper = new JsonObject();
        JsonObject obj = new JsonObject();
        String[] params = text.split("&");
        for (String param : params) {
            if (!param.contains("=")) continue;
            String[] a = param.split("=", 2);
            obj.addProperty(a[0].trim().replace("\"", ""), a[1].trim().replace("\"", ""));
        }
        if (obj.entrySet().size() > 0) wrapper.add("header", obj);
        return wrapper;
    }

    private static JsonObject findOrCreateGroup(JsonArray result, String name) {
        name = normalizeGroupName(name);
        for (JsonElement element : result) {
            JsonObject group = element.getAsJsonObject();
            if (name.equals(group.get("group").getAsString())) return group;
        }
        JsonObject group = new JsonObject();
        group.addProperty("group", name);
        group.add("channels", new JsonArray());
        result.add(group);
        return group;
    }

    public static String normalizeGroupName(String name) {
        if (name == null) return DEFAULT_GROUP_NAME;
        name = name.trim();
        if (name.isEmpty() || LEGACY_DEFAULT_GROUP_NAME.equalsIgnoreCase(name)) return DEFAULT_GROUP_NAME;
        return name;
    }

    private static void addChannel(JsonObject group, JsonObject channel) {
        JsonArray channels = group.has("channels") ? group.getAsJsonArray("channels") : new JsonArray();
        String name = DefaultConfig.safeJsonString(channel, "name", "");
        JsonObject exists = name.isEmpty() ? null : findChannel(channels, name);
        if (exists == null) {
            channels.add(channel);
        } else {
            mergeChannel(exists, channel);
        }
        group.add("channels", channels);
    }

    private static JsonObject findChannel(JsonArray channels, String name) {
        for (JsonElement element : channels) {
            if (!element.isJsonObject()) continue;
            JsonObject channel = element.getAsJsonObject();
            if (name.equals(DefaultConfig.safeJsonString(channel, "name", ""))) return channel;
        }
        return null;
    }

    private static void mergeChannel(JsonObject dst, JsonObject src) {
        mergeUrls(dst, src);
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            String key = entry.getKey();
            if ("urls".equals(key)) continue;
            if (!dst.has(key) || isEmptyValue(dst.get(key))) dst.add(key, entry.getValue());
        }
    }

    private static void mergeUrls(JsonObject dst, JsonObject src) {
        if (!src.has("urls") || !src.get("urls").isJsonArray()) return;
        JsonArray dstUrls = dst.has("urls") && dst.get("urls").isJsonArray() ? dst.getAsJsonArray("urls") : new JsonArray();
        for (JsonElement element : src.getAsJsonArray("urls")) {
            if (!element.isJsonPrimitive()) continue;
            String url = element.getAsString().trim();
            if (isUrl(url) && !containsUrl(dstUrls, url)) dstUrls.add(url);
        }
        dst.add("urls", dstUrls);
    }

    private static boolean isEmptyValue(JsonElement element) {
        if (element == null || element.isJsonNull()) return true;
        if (element.isJsonPrimitive()) return element.getAsString().trim().isEmpty();
        if (element.isJsonArray()) return element.getAsJsonArray().size() == 0;
        return element.isJsonObject() && element.getAsJsonObject().entrySet().size() == 0;
    }

    private static boolean containsUrl(JsonArray urls, String url) {
        for (JsonElement element : urls) {
            if (url.equals(element.getAsString())) return true;
        }
        return false;
    }

    private static void mergeMeta(JsonObject dst, JsonObject src) {
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            dst.add(entry.getKey(), entry.getValue());
        }
    }

    private static JsonObject buildMeta(String line) {
        JsonObject obj = new JsonObject();
        put(obj, "logo", get(line, TVG_LOGO_PATTERN));
        put(obj, "epg", get(line, TVG_URL_PATTERN));
        put(obj, "tvg-id", get(line, TVG_ID_PATTERN));
        put(obj, "tvg-name", get(line, TVG_NAME_PATTERN));
        put(obj, "tvg-chno", get(line, TVG_CHNO_PATTERN));
        put(obj, "ua", get(line, HTTP_USER_AGENT_PATTERN));
        String catchup = get(line, CATCHUP_PATTERN);
        String source = get(line, CATCHUP_SOURCE_PATTERN);
        String replace = get(line, CATCHUP_REPLACE_PATTERN);
        if (!catchup.isEmpty() || !source.isEmpty() || !replace.isEmpty()) {
            JsonObject catchupObj = new JsonObject();
            put(catchupObj, "type", catchup);
            put(catchupObj, "source", source);
            put(catchupObj, "replace", replace);
            obj.add("catchup", catchupObj);
        }
        return obj;
    }

    private static JsonObject buildSetting(String line) {
        JsonObject obj = new JsonObject();
        if (line.startsWith("ua")) put(obj, "ua", getValue(line, "ua"));
        if (line.startsWith("parse")) put(obj, "parse", getValue(line, "parse"));
        if (line.startsWith("click")) put(obj, "click", getValue(line, "click"));
        if (line.startsWith("header")) {
            String value = getValue(line, "header");
            if (!value.isEmpty()) {
                try {
                    obj.add("header", JsonParser.parseString(value).getAsJsonObject());
                } catch (Throwable ignored) {
                }
            }
        }
        if (line.startsWith("format")) put(obj, "format", getValue(line, "format"));
        if (line.startsWith("origin")) put(obj, "origin", getValue(line, "origin"));
        if (line.startsWith("referer")) put(obj, "referer", getValue(line, "referer"));
        if (line.startsWith("#EXTHTTP:")) {
            try {
                obj.add("header", JsonParser.parseString(line.split("#EXTHTTP:")[1].trim()).getAsJsonObject());
            } catch (Throwable ignored) {
            }
        }
        if (line.startsWith("#EXTVLCOPT:")) {
            if (line.contains("http-user-agent")) put(obj, "ua", getValue(line, "http-user-agent"));
            if (line.contains("http-origin")) put(obj, "origin", getValue(line, "http-origin"));
            if (line.contains("http-referrer")) put(obj, "referer", getValue(line, "http-referrer"));
        }
        if (line.startsWith("#KODIPROP:") && line.contains("manifest_type=")) {
            put(obj, "format", getValue(line, "manifest_type"));
        }
        return obj;
    }

    private static boolean isSetting(String line) {
        return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click") || line.startsWith("player") || line.startsWith("header") || line.startsWith("format") || line.startsWith("origin") || line.startsWith("referer") || line.startsWith("#EXTHTTP:") || line.startsWith("#EXTVLCOPT:") || line.startsWith("#KODIPROP:");
    }

    private static boolean isUrl(String url) {
        return !url.isEmpty() && (url.startsWith("http") || url.startsWith("rtp") || url.startsWith("rtsp") || url.startsWith("rtmp"));
    }

    private static String get(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) return matcher.group(1).trim();
        return "";
    }

    private static String getValue(String line, String key) {
        int index = line.indexOf(key + "=");
        if (index == -1) return "";
        return line.substring(index + key.length() + 1).trim().replace("\"", "");
    }

    private static void put(JsonObject obj, String key, String value) {
        if (value != null && !value.isEmpty()) obj.addProperty(key, value);
    }
}
