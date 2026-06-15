package com.github.tvbox.osc.util;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import com.google.android.exoplayer2.util.UriUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author asdfgh, FongMi
 * Based on FongMi/TV.
 * https://github.com/FongMi/TV
 */
public class M3u8 {
    private static final String TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY";
    private static final String TAG_MEDIA_DURATION = "#EXTINF";
    private static final String TAG_ENDLIST = "#EXT-X-ENDLIST";
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final String TAG_CUE_OUT = "#EXT-X-CUE-OUT";
    private static final String TAG_CUE_IN = "#EXT-X-CUE-IN";
    private static final String TAG_DATERANGE = "#EXT-X-DATERANGE";

    private static final Pattern REGEX_X_DISCONTINUITY = Pattern.compile("#EXT-X-DISCONTINUITY[\\s\\S]*?(?=#EXT-X-DISCONTINUITY|$)");
    private static final Pattern REGEX_MEDIA_DURATION = Pattern.compile(TAG_MEDIA_DURATION + ":([\\d\\.]+)\\b");
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");
    private static final Pattern REGEX_AD_SEGMENT_URI = Pattern.compile("(?i)(^|[/?&=_.-])(ad|ads|advert|advertise|advertisement|commercial|preroll|pre-roll|midroll|mid-roll|postroll|post-roll|sponsor|scte|vast|vmap)([/?&=_.-]|$)");
    public static int currentAdCount;

    public static boolean isAd(String regex) {
        return regex.contains(TAG_DISCONTINUITY) || regex.contains(TAG_MEDIA_DURATION) || regex.contains(TAG_ENDLIST) || regex.contains(TAG_KEY) || regex.contains(TAG_CUE_OUT) || regex.contains(TAG_CUE_IN) || regex.contains(TAG_DATERANGE) || M3u8.isDouble(regex);
    }

    public static String purify(String tsUrlPre, String m3u8content) {
        long start = System.currentTimeMillis();
        currentAdCount = 0;
        if (null == m3u8content || m3u8content.length() == 0) return null;
        if (m3u8content.startsWith("\ufeff")) m3u8content = m3u8content.substring(1);
        if (!m3u8content.startsWith("#EXTM3U")) return null;
        String result = removeMinorityUrl(tsUrlPre, m3u8content);
        if (result != null && currentAdCount > 0) result = get(tsUrlPre, result);
        else result = get(tsUrlPre, m3u8content);
        long cost = System.currentTimeMillis() - start;
        LOG.i("echo-fixAdM3u8Ai cost: " + cost + "ms");
        return result;
    }

    private static double maxPercent(HashMap<String, Integer> preUrlMap) {
        int maxTimes = 0, totalTimes = 0;
        for (Map.Entry<String, Integer> entry : preUrlMap.entrySet()) {
            if (entry.getValue() > maxTimes) {
                maxTimes = entry.getValue();
            }
            totalTimes += entry.getValue();
        }
        return  maxTimes*1.0 / (totalTimes*1.0);
    }
    /**
     * @author asdfgh
     * <a href="https://github.com/asdfgh"> asdfgh </a>
     */

    private static int timesNoAd = 15;
    private static String removeMinorityUrl(String tsUrlPre, String m3u8content) {
        String linesplit = "\n";
        if (m3u8content.contains("\r\n"))
            linesplit = "\r\n";
        String[] lines = m3u8content.split(linesplit);

        // First pass: count normalized media path prefixes.
        HashMap<String, Integer> preUrlMap = new HashMap<>();
        for (String line : lines) {
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            String absoluteUrl = toAbsoluteUrl(tsUrlPre, line);
            int ilast = absoluteUrl.lastIndexOf('.');
            if (ilast <= 4) {
                continue;
            }
            String preUrl = absoluteUrl.substring(0, ilast - 4);
            Integer cnt = preUrlMap.get(preUrl);
            if (cnt != null) {
                preUrlMap.put(preUrl, cnt + 1);
            } else {
                preUrlMap.put(preUrl, 1);
            }
        }
        if (preUrlMap.size() <= 1) return null;
        boolean domainFiltering = false;
        if (maxPercent(preUrlMap) < 0.8) {
            // Fallback to dominant host filtering.
            preUrlMap.clear();
            for (String line : lines) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String absoluteUrl = toAbsoluteUrl(tsUrlPre, line);
                if (!absoluteUrl.startsWith("http://") && !absoluteUrl.startsWith("https://")) {
                    return null;
                }
                int ifirst = absoluteUrl.indexOf('/', 9);
                if (ifirst <= 0) {
                    continue;
                }
                String preUrl = absoluteUrl.substring(0, ifirst);
                Integer cnt = preUrlMap.get(preUrl);
                if (cnt != null) {
                    preUrlMap.put(preUrl, cnt + 1);
                } else {
                    preUrlMap.put(preUrl, 1);
                }
            }
            if (preUrlMap.size() <= 1) return null;
            if (maxPercent(preUrlMap) < 0.8) {
                return null;
            }
            boolean allDomainsExceedThreshold = true;
            for (Integer count : preUrlMap.values()) {
                if (count <= 15) {
                    allDomainsExceedThreshold = false;
                    break;
                }
            }
            if (allDomainsExceedThreshold) return null;
            domainFiltering = true;
        }

        // Keep the most common media prefix or host.
        int maxTimes = 0;
        String maxTimesPreUrl = "";
        for (Map.Entry<String, Integer> entry : preUrlMap.entrySet()) {
            if (entry.getValue() > maxTimes) {
                maxTimesPreUrl = entry.getKey();
                maxTimes = entry.getValue();
            }
        }
        if (maxTimes == 0) return null;

        boolean dealedExtXKey = false;
        for (int i = 0; i < lines.length; ++i) {
            // Resolve the encryption key URI once.
            if (!dealedExtXKey && lines[i].startsWith("#EXT-X-KEY")) {
                String keyUrl = "";
                int start = lines[i].indexOf("URI=\"");
                if (start != -1) {
                    start += "URI=\"".length();
                    int end = lines[i].indexOf("\"", start);
                    if (end != -1) {
                        keyUrl = lines[i].substring(start, end);
                    }
                    if (!keyUrl.startsWith("http://") && !keyUrl.startsWith("https://")) {
                        String newKeyUrl = toAbsoluteUrl(tsUrlPre, keyUrl);
                        lines[i] = lines[i].replace("URI=\"" + keyUrl + "\"", "URI=\"" + newKeyUrl + "\"");
                    }
                    dealedExtXKey = true;
                }
            }
            if (lines[i].length() == 0 || lines[i].charAt(0) == '#') {
                continue;
            }
            // Filter by the selected strategy.
            String absoluteUrl = toAbsoluteUrl(tsUrlPre, lines[i]);
            if (!domainFiltering) {
                if (absoluteUrl.startsWith(maxTimesPreUrl)) {
                    lines[i] = absoluteUrl;
                } else {
                    if (i > 0 && lines[i - 1].length() > 0 && lines[i - 1].charAt(0) == '#') {
                        lines[i - 1] = "";
                    }
                    lines[i] = "";
                    currentAdCount+=1;
                }
            } else {
                int ifirst = absoluteUrl.indexOf('/', 9);
                String domain = (ifirst > 0) ? absoluteUrl.substring(0, ifirst) : absoluteUrl;
                Integer cnt = preUrlMap.get(domain);
                if (domain.equals(maxTimesPreUrl) || (cnt != null && cnt > timesNoAd)) {
                    lines[i] = absoluteUrl;
                } else {
                    if (i > 0 && lines[i - 1].length() > 0 && lines[i - 1].charAt(0) == '#') {
                        lines[i - 1] = "";
                    }
                    lines[i] = "";
                    currentAdCount+=1;
                }
            }
        }
        return String.join(linesplit, lines);
    }

    private static String get(String tsUrlPre, String m3u8Content) {
        String line = resolveContent(tsUrlPre, m3u8Content);
        List<String> ads = getRegex(tsUrlPre);
        if (ads != null && !ads.isEmpty()) line = clean(line, ads);
        line = cleanCommonAdMarkers(tsUrlPre, line);
        return cleanDiscontinuityGroups(tsUrlPre, line);
    }

    private static String resolveContent(String tsUrlPre, String m3u8Content) {
        m3u8Content = m3u8Content.replaceAll("\r\n", "\n");
        StringBuilder sb = new StringBuilder();
        for (String line : m3u8Content.split("\n")) {
            sb.append(shouldResolve(line) ? resolve(tsUrlPre, line.trim()) : line).append("\n");
        }
        return sb.toString();
    }

    private static List<String> getRegex(String tsUrlPre) {
        HashMap<String, ArrayList<String>> hostsRegex = VideoParseRuler.getHostsRegex();
        List<String> list = new ArrayList<>();
        for (String host : hostsRegex.keySet()) {
            if (!tsUrlPre.contains(host)) continue;
            if (hostsRegex.get(host) == null) continue;
            list = hostsRegex.get(host);
            break;
        }
        return list;
    }

    private static String clean(String line, List<String> ads) {
        boolean scan = false;
        for (String ad : ads) {
            if (ad.contains(TAG_DISCONTINUITY) || ad.contains(TAG_MEDIA_DURATION)) line = scanAd(line,ad);
            else if (isDouble(ad)) scan = true;
        }
        return scan ? scan(line, ads) : line;
    }

    private static String cleanCommonAdMarkers(String tsUrlPre, String m3u8Content) {
        String line = resolveContent(tsUrlPre, m3u8Content);
        StringBuilder sb = new StringBuilder();
        List<String> pending = new ArrayList<>();
        boolean inAdBreak = false;
        boolean changed = false;

        for (String raw : line.split("\n", -1)) {
            String item = raw.trim();
            if (item.length() == 0) {
                if (pending.isEmpty()) sb.append(raw).append("\n");
                else pending.add(raw);
                continue;
            }
            if (item.startsWith("#")) {
                if (item.startsWith(TAG_CUE_IN)) {
                    if (inAdBreak || hasAdSignal(pending)) {
                        inAdBreak = false;
                        pending.clear();
                        changed = true;
                        continue;
                    }
                }
                if (isAdBreakStart(item)) {
                    flush(sb, pending);
                    inAdBreak = true;
                    pending.add(raw);
                    changed = true;
                    continue;
                }
                if (inAdBreak) {
                    pending.add(raw);
                    changed = true;
                    continue;
                }
                if (isStandaloneAdTag(item)) {
                    flush(sb, pending);
                    currentAdCount += 1;
                    changed = true;
                    continue;
                }
                if (isSegmentTag(item) || isAdSignalTag(item)) {
                    pending.add(raw);
                } else {
                    flush(sb, pending);
                    sb.append(raw).append("\n");
                }
                continue;
            }

            if (inAdBreak || hasAdSignal(pending) || isAdSegmentUri(item)) {
                pending.clear();
                currentAdCount += 1;
                changed = true;
                continue;
            }
            flush(sb, pending);
            sb.append(raw).append("\n");
        }

        if (!inAdBreak) flush(sb, pending);
        return changed ? sb.toString() : line;
    }

    private static void flush(StringBuilder sb, List<String> pending) {
        for (String line : pending) sb.append(line).append("\n");
        pending.clear();
    }

    private static boolean hasAdSignal(List<String> pending) {
        for (String line : pending) {
            if (isAdBreakStart(line.trim()) || isAdSignalTag(line.trim())) return true;
        }
        return false;
    }

    private static boolean isAdBreakStart(String line) {
        return line.startsWith(TAG_CUE_OUT);
    }

    private static boolean isAdSignalTag(String line) {
        if (line.startsWith("#EXT-OATCLS-SCTE35")) return true;
        if (line.startsWith("#EXT-X-SCTE35")) return true;
        if (line.startsWith("#EXT-X-SPLICEPOINT-SCTE35")) return true;
        if (line.startsWith("#EXT-X-ASSET")) return true;
        if (line.startsWith("#EXT-X-VMAP-AD-BREAK")) return true;
        return false;
    }

    private static boolean isSegmentTag(String line) {
        return line.startsWith(TAG_MEDIA_DURATION) || line.startsWith("#EXT-X-BYTERANGE") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME") || line.startsWith("#EXT-X-DISCONTINUITY") || line.startsWith("#EXT-X-PART") || line.startsWith("#EXT-X-PRELOAD-HINT");
    }

    private static boolean isStandaloneAdTag(String line) {
        if (!line.startsWith(TAG_DATERANGE)) return false;
        return isAdLikeText(line) || line.contains("X-ASSET-URI") || line.contains("X-ASSET-LIST");
    }

    private static boolean isAdLikeText(String line) {
        String lower = line.toLowerCase();
        return lower.contains("scte") || lower.contains("cue") || lower.contains("interstitial") || lower.contains("vmap") || lower.contains("vast") || lower.contains("advert") || lower.contains("commercial") || lower.contains("ad-") || lower.contains("ad_") || lower.contains("ad.");
    }

    private static boolean isAdSegmentUri(String line) {
        return REGEX_AD_SEGMENT_URI.matcher(line).find();
    }

    private static String cleanDiscontinuityGroups(String tsUrlPre, String m3u8Content) {
        String line = resolveContent(tsUrlPre, m3u8Content);
        String[] lines = line.split("\n");
        List<Group> groups = buildDiscontinuityGroups(lines);
        if (groups.size() < 3) return line;
        Group main = findMainGroup(groups);
        if (main == null || main.segmentCount < 3) return line;

        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (Group group : groups) {
            if (shouldDropGroup(group, main)) {
                currentAdCount += group.segmentCount;
                changed = true;
                continue;
            }
            group.appendTo(sb);
        }
        return changed ? sb.toString() : line;
    }

    private static List<Group> buildDiscontinuityGroups(String[] lines) {
        List<Group> groups = new ArrayList<>();
        Group group = new Group();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith(TAG_DISCONTINUITY) && group.hasMedia()) {
                groups.add(group);
                group = new Group();
            }
            group.add(raw);
        }
        if (group.hasMedia() || !group.lines.isEmpty()) groups.add(group);
        return groups;
    }

    private static Group findMainGroup(List<Group> groups) {
        Group main = null;
        for (Group group : groups) {
            if (group.segmentCount == 0) continue;
            if (main == null || group.score() > main.score()) main = group;
        }
        return main;
    }

    private static boolean shouldDropGroup(Group group, Group main) {
        if (group == main || group.segmentCount == 0) return false;
        boolean shortGroup = group.segmentCount <= 2 || (main.totalDuration > 0 && group.totalDuration > 0 && group.totalDuration < main.totalDuration * 0.18);
        boolean differentHost = main.host.length() > 0 && group.host.length() > 0 && !main.host.equals(group.host);
        boolean differentPath = main.pathPrefix.length() > 0 && group.pathPrefix.length() > 0 && !main.pathPrefix.equals(group.pathPrefix);
        boolean adLike = group.adLikeCount > 0 || differentHost || (group.segmentCount <= 2 && differentPath);
        return shortGroup && adLike;
    }

    private static String hostOf(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
        int start = url.indexOf("://") + 3;
        int end = url.indexOf('/', start);
        return end > start ? url.substring(start, end) : url.substring(start);
    }

    private static String pathPrefixOf(String url) {
        String clean = url;
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        int slash = clean.lastIndexOf('/');
        return slash > 0 ? clean.substring(0, slash + 1) : "";
    }

    private static String toAbsoluteUrl(String base, String url) {
        if (url == null) return "";
        String line = url.trim();
        if (line.length() == 0 || line.startsWith("http://") || line.startsWith("https://")) return line;
        return UriUtil.resolve(base, line);
    }

    private static class Group {
        private final List<String> lines = new ArrayList<>();
        private int segmentCount = 0;
        private int adLikeCount = 0;
        private double totalDuration = 0;
        private String host = "";
        private String pathPrefix = "";

        private void add(String raw) {
            lines.add(raw);
            String line = raw.trim();
            Matcher matcher = REGEX_MEDIA_DURATION.matcher(line);
            if (matcher.find()) {
                try {
                    totalDuration += Double.parseDouble(matcher.group(1));
                } catch (Exception ignored) {
                }
            }
            if (line.length() == 0 || line.startsWith("#")) {
                if (isAdSignalTag(line) || isStandaloneAdTag(line)) adLikeCount += 1;
                return;
            }
            segmentCount += 1;
            if (isAdSegmentUri(line)) adLikeCount += 1;
            if (host.length() == 0) host = hostOf(line);
            if (pathPrefix.length() == 0) pathPrefix = pathPrefixOf(line);
        }

        private boolean hasMedia() {
            return segmentCount > 0;
        }

        private void appendTo(StringBuilder sb) {
            for (String line : lines) sb.append(line).append("\n");
        }

        private double score() {
            return totalDuration > 0 ? totalDuration : segmentCount;
        }
    }

    private static String scanAd(String line,String TAG_AD) {
        Matcher m1 = getPattern(TAG_AD).matcher(line);
        List<String> needRemoveAd = new ArrayList<>();
        while (m1.find()) {
            String group = m1.group();
            String groupCleaned = group.replace(TAG_ENDLIST, "");
            Matcher m2 = REGEX_MEDIA_DURATION.matcher(group);
            int tCount = 0;
            while (m2.find()) {
                tCount+=1;
            }
            needRemoveAd.add(groupCleaned);
            currentAdCount+=tCount;
        }
        for (String rem : needRemoveAd) {
            line = line.replace(rem, "");
        }
        return line;
    }

    private static String scan(String line, List<String> ads) {
        Matcher m1 = REGEX_X_DISCONTINUITY.matcher(line);
        List<String> needRemoveAd = new ArrayList<>();
        while (m1.find()) {
            String group = m1.group();
            String groupCleaned = group.replace(TAG_ENDLIST, "");
            Matcher m2 = REGEX_MEDIA_DURATION.matcher(group);
            BigDecimal ft = BigDecimal.ZERO,lt = BigDecimal.ZERO,t = BigDecimal.ZERO;
            int tCount = 0;
            while (m2.find()) {
                if (ft.equals(BigDecimal.ZERO))ft = new BigDecimal(m2.group(1));
                lt = new BigDecimal(m2.group(1));
                t = t.add(lt);
                tCount+=1;
            }

            String ftStr = ft.toString(),ltStr = lt.toString(),tStr = t.toString();
            for (String ad : ads) {
                if (ad.startsWith("-")) {
                    String adClean = ad.substring(1);
                    // Match the last segment duration.
                    if (ltStr.startsWith(adClean)) {
                        needRemoveAd.add(groupCleaned);
                        currentAdCount+=tCount;
                        break;
                    }
                } else {
                    // Match the first segment duration or total ad duration.
                    if (ftStr.startsWith(ad) || tStr.startsWith(ad)) {
                        needRemoveAd.add(groupCleaned);
                        currentAdCount+=tCount;
                        break;
                    }
                }
            }
        }
        for (String rem : needRemoveAd) {
            line = line.replace(rem, "");
        }
        return line;
    }

    private static boolean isDouble(String ad) {
        try {
            return Double.parseDouble(ad) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean shouldResolve(String line) {
        String item = line.trim();
        if (item.length() == 0) return false;
        return (!item.startsWith("#") && !item.startsWith("http")) || item.startsWith(TAG_KEY);
    }

    private static String resolve(String base, String line) {
        if (line.startsWith(TAG_KEY)) {
            Matcher matcher = REGEX_URI.matcher(line);
            String value = matcher.find() ? matcher.group(1) : null;
            return value == null ? line : line.replace(value, UriUtil.resolve(base, value));
        } else {
            return UriUtil.resolve(base, line);
        }
    }
}
