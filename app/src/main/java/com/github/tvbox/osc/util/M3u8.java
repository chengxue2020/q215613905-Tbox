package com.github.tvbox.osc.util;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import com.google.android.exoplayer2.util.UriUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String TAG_MAP = "#EXT-X-MAP";
    private static final String TAG_CUE_OUT = "#EXT-X-CUE-OUT";
    private static final String TAG_CUE_IN = "#EXT-X-CUE-IN";
    private static final String TAG_DATERANGE = "#EXT-X-DATERANGE";

    private static final Pattern REGEX_X_DISCONTINUITY = Pattern.compile("#EXT-X-DISCONTINUITY[\\s\\S]*?(?=#EXT-X-DISCONTINUITY|$)");
    private static final Pattern REGEX_MEDIA_DURATION = Pattern.compile(TAG_MEDIA_DURATION + ":([\\d\\.]+)\\b");
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");

    // 增强：广告片段 URL 特征识别（去广告接口常用规则）
    private static final Pattern REGEX_AD_SEGMENT_URI = Pattern.compile("(?i)(^|[/?&=_.-])(ads?|adv|advert(ise(ment)?)?|commercial|preroll|pre-roll|midroll|mid-roll|postroll|post-roll|sponsor|scte|vast|vmap|interstitial|bumper)([/?&=_.-]|$)");

    // 增强：广告域名特征（常见广告CDN）
    private static final String[] AD_DOMAIN_KEYWORDS = {
        "adservice", "adserver", "adsystem", "doubleclick", "googlesyndication",
        "advertising", "2mdn.net", "moatads", "scorecardresearch", "quantserve"
    };

    private static final int MAX_FRAME_RATE_AD_BLOCK_SIZE = 12;
    private static final Map<Integer, Set<BigDecimal>> FRAME_RATE_FEATURES = prepareFrameRateFeatures();

    public static int currentAdCount;

    public static boolean isAd(String regex) {
        return regex.contains(TAG_DISCONTINUITY) || regex.contains(TAG_MEDIA_DURATION) || regex.contains(TAG_ENDLIST) || regex.contains(TAG_KEY) || regex.contains(TAG_CUE_OUT) || regex.contains(TAG_CUE_IN) || regex.contains(TAG_DATERANGE) || M3u8.isDouble(regex);
    }

    public static String purify(String tsUrlPre, String m3u8content) {
//        LOG.i("echo-fixAdM3u8 m3u8content: " +m3u8content);
        long start = System.currentTimeMillis();
        currentAdCount = 0;
        if (null == m3u8content || m3u8content.length() == 0) return null;
        if (m3u8content.startsWith("\ufeff")) m3u8content = m3u8content.substring(1);
        if (!m3u8content.startsWith("#EXTM3U")) return null;

        // Count total segments for final safety check
        int totalSegments = 0;
        String[] lines = m3u8content.split(m3u8content.contains("\r\n") ? "\r\n" : "\n");
        for (String line : lines) {
            if (line.length() > 0 && line.charAt(0) != '#') {
                totalSegments++;
            }
        }

        String result = removeMinorityUrl(tsUrlPre, m3u8content);
        if (result != null && currentAdCount > 0) result = get(tsUrlPre, result);
        else result = get(tsUrlPre, m3u8content);
        result = keepVodEndList(m3u8content, result);

        // Final safety check: if too many segments removed, return original content
        if (totalSegments > 0 && currentAdCount > totalSegments * 0.5) {
            LOG.e("echo-fixAdM3u8 ERROR: removed too many segments " + currentAdCount + "/" + totalSegments + ", using original content");
            currentAdCount = 0;
            result = m3u8content;
        }
        if (currentAdCount > 0 && !isPlayableMediaPlaylist(result)) {
            LOG.e("echo-fixAdM3u8 ERROR: invalid playlist after ad removal, using original content");
            currentAdCount = 0;
            result = m3u8content;
        }

        long cost = System.currentTimeMillis() - start;
        LOG.i("echo-fixAdM3u8 cost: " + cost + "ms, removed: " + currentAdCount + " segments");
//        LOG.i("echo-fixAdM3u8 result: " + result );
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

    private static int timesNoAd = 15;
    private static String removeMinorityUrl(String tsUrlPre, String m3u8content) {
        String linesplit = "\n";
        if (m3u8content.contains("\r\n"))
            linesplit = "\r\n";
        String[] lines = m3u8content.split(linesplit);

        // Count total segments for safety check
        int totalSegments = 0;
        for (String line : lines) {
            if (line.length() > 0 && line.charAt(0) != '#') {
                totalSegments++;
            }
        }

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

        // Diagnostic logging
        LOG.i("echo-fixAdM3u8 URL pattern count: " + preUrlMap.size() + ", maxTimes: " + maxTimes + ", total: " + totalSegments);

        StringBuilder filtered = new StringBuilder();
        List<String> pendingSegmentTags = new ArrayList<>();
        for (int i = 0; i < lines.length; ++i) {
            String item = lines[i].trim();
            if (item.length() == 0) {
                if (pendingSegmentTags.isEmpty()) appendLine(filtered, lines[i], linesplit);
                else pendingSegmentTags.add(lines[i]);
                continue;
            }
            if (item.charAt(0) == '#') {
                String output = hasUriAttribute(item) ? resolveUriLine(tsUrlPre, lines[i]) : lines[i];
                if (isSegmentTag(item)) pendingSegmentTags.add(output);
                else {
                    flush(filtered, pendingSegmentTags, linesplit);
                    appendLine(filtered, output, linesplit);
                }
                continue;
            }

            String absoluteUrl = toAbsoluteUrl(tsUrlPre, lines[i]);
            if (shouldKeepMediaUrl(absoluteUrl, domainFiltering, maxTimesPreUrl, preUrlMap)) {
                flush(filtered, pendingSegmentTags, linesplit);
                appendLine(filtered, absoluteUrl, linesplit);
            } else {
                pendingSegmentTags.clear();
                currentAdCount += 1;
            }
        }

        // Safety check: if removal ratio is too high, likely a false positive
        if (totalSegments > 0 && currentAdCount > totalSegments * 0.3) {
            LOG.i("echo-fixAdM3u8 suspicious ad count: " + currentAdCount + "/" + totalSegments + ", skipping URL filtering");
            currentAdCount = 0;  // Reset to avoid affecting subsequent filters
            return null;  // Skip this filtering method
        }

        return normalizeMediaPlaylist(filtered.toString());
    }

    private static String get(String tsUrlPre, String m3u8Content) {
        String line = resolveContent(tsUrlPre, m3u8Content);
        List<String> ads = getRegex(tsUrlPre);
        if (ads != null && !ads.isEmpty()) line = clean(line, ads);
        line = cleanCommonAdMarkers(line);
        if (hasEndList(line) && line.contains(TAG_DISCONTINUITY)) {
            line = cleanDecimalPrecisionGroups(line);
            line = cleanFrameRateGroups(line);
        }
        return cleanDiscontinuityGroups(line);
    }

    /**
     * 正片切片的 EXTINF 小数位数通常稳定，广告素材拼接后经常出现另一种精度。
    * 只删除精度完全不同的短块，不处理块内混合精度，避免误删正常切片。
    */
    private static String cleanDecimalPrecisionGroups(String m3u8Content) {
        List<Group> groups = buildDiscontinuityGroups(m3u8Content.split("\\n"));
        if (groups.size() < 2) return m3u8Content;

        Map<Integer, Integer> precisionCounts = new HashMap<>();
        int totalSegments = 0;
        for (Group group : groups) {
            for (String raw : group.lines) {
                int precision = getDecimalPrecision(raw);
                if (precision < 0) continue;
                totalSegments += 1;
                Integer count = precisionCounts.get(precision);
                precisionCounts.put(precision, count == null ? 1 : count + 1);
            }
        }
        if (totalSegments < 8 || precisionCounts.size() < 2) return m3u8Content;

        int majorPrecision = -1;
        int majorCount = 0;
        for (Map.Entry<Integer, Integer> entry : precisionCounts.entrySet()) {
            if (entry.getValue() > majorCount) {
                majorPrecision = entry.getKey();
                majorCount = entry.getValue();
            }
        }
        if (majorPrecision < 0 || majorCount * 1.0 / totalSegments < 0.7) return m3u8Content;

        boolean[] removeGroups = new boolean[groups.size()];
        int removableSegments = 0;
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (i == groups.size() - 1 || group.segmentCount == 0 || group.segmentCount > MAX_FRAME_RATE_AD_BLOCK_SIZE) continue;

            DecimalPrecisionStats stats = getDecimalPrecisionStats(group, majorPrecision);
            if (stats.total > 0 && stats.mismatched == stats.total) {
                removeGroups[i] = true;
                removableSegments += group.segmentCount;
            }
        }

        if (removableSegments == 0 || removableSegments > getAdSegmentLimit(m3u8Content)
                || removableSegments > totalSegments * 0.3) return m3u8Content;

        StringBuilder sb = new StringBuilder();
        int removedBlocks = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (removeGroups[i]) {
                currentAdCount += groups.get(i).segmentCount;
                removedBlocks += 1;
            } else {
                groups.get(i).appendTo(sb);
            }
        }
        LOG.i("echo-fixAdM3u8 decimal precision detected: major=" + majorPrecision + ", blocks=" + removedBlocks + ", removed=" + removableSegments);
        return normalizeMediaPlaylist(sb.toString());
    }

    private static DecimalPrecisionStats getDecimalPrecisionStats(Group group, int majorPrecision) {
        DecimalPrecisionStats stats = new DecimalPrecisionStats();
        for (String raw : group.lines) {
            int precision = getDecimalPrecision(raw);
            if (precision < 0) continue;
            stats.total += 1;
            if (precision != majorPrecision) stats.mismatched += 1;
        }
        return stats;
    }

    private static int getDecimalPrecision(String line) {
        int start = getExtInfValueStart(line);
        if (start < 0) return -1;
        int end = getExtInfValueEnd(line, start);
        int dot = line.indexOf('.', start);
        return dot < 0 || dot >= end ? 0 : end - dot - 1;
    }

    /**
     * 广告通常由不同素材拼接，切片时长的小数部分会呈现不同的帧率特征。
    * 仅对点播播放列表中的短不连续块执行，避免影响直播和正常长片段。
    */
    private static String cleanFrameRateGroups(String m3u8Content) {
        List<Group> groups = buildDiscontinuityGroups(m3u8Content.split("\\n"));
        if (groups.size() < 2) return m3u8Content;

        int masterFrameRate = findDominantFrameRate(groups);
        if (masterFrameRate == 0) return m3u8Content;

        int removableSegments = 0;
        boolean[] removeGroups = new boolean[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (i == groups.size() - 1 || group.segmentCount == 0 || group.segmentCount > MAX_FRAME_RATE_AD_BLOCK_SIZE) continue;

            FrameRateStats stats = getFrameRateStats(group, masterFrameRate);
            if (stats.mismatched > 0 && stats.mismatched >= stats.matched) {
                removeGroups[i] = true;
                removableSegments += group.segmentCount;
            }
        }

        int segmentLimit = getAdSegmentLimit(m3u8Content);
        if (removableSegments == 0 || removableSegments > segmentLimit) return m3u8Content;

        StringBuilder sb = new StringBuilder();
        int removedBlocks = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (removeGroups[i]) {
                currentAdCount += groups.get(i).segmentCount;
                removedBlocks += 1;
            } else {
                groups.get(i).appendTo(sb);
            }
        }
        LOG.i("echo-fixAdM3u8 frame rate detected: master=" + masterFrameRate + ", blocks=" + removedBlocks + ", removed=" + removableSegments);
        return normalizeMediaPlaylist(sb.toString());
    }

    private static int findDominantFrameRate(List<Group> groups) {
        int count30 = 0;
        int count25 = 0;
        int count24 = 0;
        for (Group group : groups) {
            for (String raw : group.lines) {
                if (getExtInfValueStart(raw) < 0) continue;
                int frameRate = getExclusiveFrameRate(parseExtInfDuration(raw));
                if (frameRate == 30) count30 += 1;
                else if (frameRate == 25) count25 += 1;
                else if (frameRate == 24) count24 += 1;
            }
        }

        int max = Math.max(count30, Math.max(count25, count24));
        if (max < 2) return 0;
        if ((count30 == max ? 1 : 0) + (count25 == max ? 1 : 0) + (count24 == max ? 1 : 0) != 1) return 0;
        return count30 == max ? 30 : (count25 == max ? 25 : 24);
    }

    private static FrameRateStats getFrameRateStats(Group group, int masterFrameRate) {
        FrameRateStats stats = new FrameRateStats();
        for (String raw : group.lines) {
            if (getExtInfValueStart(raw) < 0) continue;
            int frameRate = getExclusiveFrameRate(parseExtInfDuration(raw));
            if (frameRate == masterFrameRate) stats.matched += 1;
            else if (frameRate != 0) stats.mismatched += 1;
        }
        return stats;
    }

    private static int getExclusiveFrameRate(BigDecimal duration) {
        boolean is30 = isFrameAligned(duration, 30);
        boolean is25 = isFrameAligned(duration, 25);
        boolean is24 = isFrameAligned(duration, 24);
        if (is30 && !is25 && !is24) return 30;
        if (is25 && !is30 && !is24) return 25;
        if (is24 && !is30 && !is25) return 24;
        return 0;
    }

    private static boolean isFrameAligned(BigDecimal duration, int frameRate) {
        if (duration == null) return false;
        Set<BigDecimal> features = FRAME_RATE_FEATURES.get(frameRate);
        if (features == null) return false;
        BigDecimal fraction = duration.remainder(BigDecimal.ONE).abs().stripTrailingZeros();
        return features.contains(fraction);
    }

    private static Map<Integer, Set<BigDecimal>> prepareFrameRateFeatures() {
        Map<Integer, Set<BigDecimal>> features = new HashMap<>();
        features.put(30, createFrameRateFeatures(30, true));
        features.put(25, createFrameRateFeatures(25, false));
        features.put(24, createFrameRateFeatures(24, true));
        return features;
    }

    private static Set<BigDecimal> createFrameRateFeatures(int frameRate, boolean includeNtsc) {
        Set<BigDecimal> features = new HashSet<>();
        addFrameRateFeatures(features, frameRate, frameRate);
        if (includeNtsc) addFrameRateFeatures(features, frameRate / 1.001d, frameRate * 10);
        return features;
    }

    private static void addFrameRateFeatures(Set<BigDecimal> features, double frameRate, int maxFrames) {
        BigDecimal rate = BigDecimal.valueOf(frameRate);
        for (int frame = 1; frame <= maxFrames; frame++) {
            BigDecimal fraction = BigDecimal.valueOf(frame).divide(rate, 10, BigDecimal.ROUND_HALF_UP).remainder(BigDecimal.ONE);
            for (int scale = 3; scale <= 6; scale++) {
                BigDecimal value = fraction.setScale(scale, BigDecimal.ROUND_HALF_UP).stripTrailingZeros();
                if (value.compareTo(BigDecimal.ZERO) != 0) features.add(value);
            }
        }
    }

    private static BigDecimal parseExtInfDuration(String line) {
        int start = getExtInfValueStart(line);
        if (start < 0) return BigDecimal.ZERO;
        int end = getExtInfValueEnd(line, start);
        try {
            return new BigDecimal(line.substring(start, end)).stripTrailingZeros();
        } catch (Exception ignored) {
        }
        return BigDecimal.ZERO;
    }

    private static int getExtInfValueStart(String line) {
        if (line == null) return -1;
        int length = line.length();
        int start = 0;
        while (start < length && line.charAt(start) <= ' ') start += 1;
        if (!line.startsWith(TAG_MEDIA_DURATION, start)) return -1;
        start += TAG_MEDIA_DURATION.length();
        if (start >= length || line.charAt(start) != ':') return -1;
        start += 1;
        while (start < length && line.charAt(start) <= ' ') start += 1;
        return start < length ? start : -1;
    }

    private static int getExtInfValueEnd(String line, int start) {
        int end = line.indexOf(',', start);
        if (end < 0) end = line.length();
        while (end > start && line.charAt(end - 1) <= ' ') end -= 1;
        return end;
    }

    private static int getAdSegmentLimit(String m3u8Content) {
        BigDecimal totalDuration = BigDecimal.ZERO;
        for (String raw : m3u8Content.split("\\n")) totalDuration = totalDuration.add(parseExtInfDuration(raw));
        double totalMinutes = totalDuration.doubleValue() / 60;
        if (totalMinutes <= 30) return 18;
        if (totalMinutes <= 60) return 24;
        if (totalMinutes <= 90) return 30;
        return 36;
    }

    private static class FrameRateStats {
        private int matched;
        private int mismatched;
    }

    private static class DecimalPrecisionStats {
        private int total;
        private int mismatched;
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

    private static String cleanCommonAdMarkers(String line) {
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

            // 增强：检查 URL 特征和域名特征
            if (inAdBreak || hasAdSignal(pending) || isAdSegmentUri(item) || hasAdDomain(item)) {
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

    private static void flush(StringBuilder sb, List<String> pending, String linesplit) {
        for (String line : pending) appendLine(sb, line, linesplit);
        pending.clear();
    }

    private static void appendLine(StringBuilder sb, String line, String linesplit) {
        sb.append(line).append(linesplit);
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
        // 增强：支持更多广告信号标记
        if (line.startsWith("#EXT-OATCLS-SCTE35")) return true;
        if (line.startsWith("#EXT-X-SCTE35")) return true;
        if (line.startsWith("#EXT-X-SPLICEPOINT-SCTE35")) return true;
        if (line.startsWith("#EXT-X-CUE")) return true;  // 新增
        if (line.startsWith("#EXT-X-ASSET")) return true;
        if (line.startsWith("#EXT-X-VMAP-AD-BREAK")) return true;
        if (line.startsWith("#EXT-X-AD")) return true;  // 新增
        if (line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) return false;  // 排除正常标记
        return false;
    }

    private static boolean isSegmentTag(String line) {
        if (line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) return false;
        return line.startsWith(TAG_MEDIA_DURATION) || line.startsWith("#EXT-X-BYTERANGE") || line.startsWith("#EXT-X-PROGRAM-DATE-TIME") || line.startsWith("#EXT-X-DISCONTINUITY") || line.startsWith("#EXT-X-PART") || line.startsWith("#EXT-X-PRELOAD-HINT");
    }

    private static boolean isStandaloneAdTag(String line) {
        if (!line.startsWith(TAG_DATERANGE)) return false;
        return isAdLikeText(line) || line.contains("X-ASSET-URI") || line.contains("X-ASSET-LIST");
    }

    private static boolean isAdLikeText(String line) {
        String lower = line.toLowerCase();
        return lower.contains("scte") || lower.contains("cue") || lower.contains("interstitial") ||
               lower.contains("vmap") || lower.contains("vast") || lower.contains("advert") ||
               lower.contains("commercial") || lower.contains("ad-") || lower.contains("ad_") ||
               lower.contains("ad.") || lower.contains("preroll") || lower.contains("midroll") ||
               lower.contains("postroll") || lower.contains("bumper");  // 增强
    }

    private static boolean isAdSegmentUri(String line) {
        return REGEX_AD_SEGMENT_URI.matcher(line).find();
    }

    // 增强：检查是否包含广告域名特征
    private static boolean hasAdDomain(String url) {
        String lower = url.toLowerCase();
        for (String keyword : AD_DOMAIN_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String cleanDiscontinuityGroups(String m3u8Content) {
        String[] lines = m3u8Content.split("\n");
        List<Group> groups = buildDiscontinuityGroups(lines);
        if (groups.size() < 3) return m3u8Content;
        Group main = findMainGroup(groups);
        if (main == null || main.segmentCount < 3) return m3u8Content;

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
        return changed ? sb.toString() : m3u8Content;
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

        // 增强：更严格的广告识别条件
        boolean shortGroup = group.segmentCount <= 2 ||
                           (main.totalDuration > 0 && group.totalDuration > 0 &&
                            group.totalDuration < main.totalDuration * 0.18);

        boolean differentHost = main.host.length() > 0 && group.host.length() > 0 &&
                               !main.host.equals(group.host);

        boolean differentPath = main.pathPrefix.length() > 0 && group.pathPrefix.length() > 0 &&
                               !main.pathPrefix.equals(group.pathPrefix);

        // 增强：检查广告域名和URL特征
        boolean hasAdFeature = group.adLikeCount > 0 || hasAdDomain(group.host) ||
                              isAdSegmentUri(group.pathPrefix);

        boolean adLike = hasAdFeature || differentHost || (group.segmentCount <= 2 && differentPath);

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

    private static boolean shouldKeepMediaUrl(String absoluteUrl, boolean domainFiltering, String maxTimesPreUrl, HashMap<String, Integer> preUrlMap) {
        if (!domainFiltering) return absoluteUrl.startsWith(maxTimesPreUrl);
        int ifirst = absoluteUrl.indexOf('/', 9);
        String domain = (ifirst > 0) ? absoluteUrl.substring(0, ifirst) : absoluteUrl;
        Integer cnt = preUrlMap.get(domain);
        return domain.equals(maxTimesPreUrl) || (cnt != null && cnt > timesNoAd);
    }

    private static boolean hasUriAttribute(String line) {
        return line.startsWith(TAG_KEY) || line.startsWith(TAG_MAP);
    }

    private static String resolveUriLine(String base, String line) {
        Matcher matcher = REGEX_URI.matcher(line);
        String value = matcher.find() ? matcher.group(1) : null;
        return value == null ? line : line.replace(value, UriUtil.resolve(base, value));
    }

    private static String normalizeMediaPlaylist(String content) {
        StringBuilder sb = new StringBuilder();
        boolean seenMedia = false;
        boolean hasPendingDiscontinuity = false;
        String pendingDiscontinuity = "";
        for (String raw : content.replaceAll("\r\n", "\n").split("\n", -1)) {
            String item = raw.trim();
            if (isDiscontinuityTag(item)) {
                if (seenMedia && !hasPendingDiscontinuity) {
                    pendingDiscontinuity = raw;
                    hasPendingDiscontinuity = true;
                }
                continue;
            }
            if (hasPendingDiscontinuity) {
                if (item.length() == 0) continue;
                if (!item.startsWith(TAG_ENDLIST)) sb.append(pendingDiscontinuity).append("\n");
                hasPendingDiscontinuity = false;
            }
            if (item.length() == 0 && sb.length() == 0) continue;
            sb.append(raw).append("\n");
            if (isMediaUriLine(item)) seenMedia = true;
        }
        return sb.toString();
    }

    private static boolean isPlayableMediaPlaylist(String content) {
        if (content == null || !content.startsWith("#EXTM3U")) return false;
        int mediaCount = 0;
        boolean pendingExtInf = false;
        for (String raw : content.replaceAll("\r\n", "\n").split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            if (line.startsWith(TAG_MEDIA_DURATION)) {
                if (pendingExtInf) return false;
                pendingExtInf = true;
            } else if (isMediaUriLine(line)) {
                mediaCount += 1;
                pendingExtInf = false;
            } else if (line.startsWith(TAG_ENDLIST) && pendingExtInf) {
                return false;
            }
        }
        return mediaCount > 0 && !pendingExtInf;
    }

    private static String keepVodEndList(String original, String result) {
        if (result == null) return null;
        if (!hasEndList(original) || hasEndList(result)) return result;
        return result + (result.endsWith("\n") ? "" : "\n") + TAG_ENDLIST + "\n";
    }

    private static boolean hasEndList(String content) {
        if (content == null) return false;
        for (String raw : content.replaceAll("\r\n", "\n").split("\n")) {
            if (raw.trim().startsWith(TAG_ENDLIST)) return true;
        }
        return false;
    }

    private static boolean isMediaUriLine(String line) {
        return line.length() > 0 && !line.startsWith("#");
    }

    private static boolean isDiscontinuityTag(String line) {
        return line.startsWith(TAG_DISCONTINUITY) && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE");
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
            int durationStart = getExtInfValueStart(line);
            if (durationStart >= 0) {
                int durationEnd = getExtInfValueEnd(line, durationStart);
                try {
                    totalDuration += Double.parseDouble(line.substring(durationStart, durationEnd));
                } catch (Exception ignored) {
                }
            }
            if (line.length() == 0 || line.startsWith("#")) {
                if (isAdSignalTag(line) || isStandaloneAdTag(line)) adLikeCount += 1;
                return;
            }
            segmentCount += 1;
            if (isAdSegmentUri(line) || hasAdDomain(line)) adLikeCount += 1;  // 增强
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
        return (!item.startsWith("#") && !item.startsWith("http")) || hasUriAttribute(item);
    }

    private static String resolve(String base, String line) {
        if (hasUriAttribute(line)) {
            return resolveUriLine(base, line);
        } else {
            return UriUtil.resolve(base, line);
        }
    }
}
