package com.github.tvbox.osc.bean;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Danmu {
    private static final Pattern D_TAG_PATTERN = Pattern.compile(
            "<d\\s+[^>]*\\bp\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</d>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final List<Data> data = new ArrayList<>();

    public static Danmu fromXml(String xml) {
        Danmu danmu = new Danmu();
        if (TextUtils.isEmpty(xml)) return danmu;
        String fixedXml = escapeIllegalEntities(xml);
        if (parseByXmlPull(danmu, fixedXml)) return danmu;
        danmu.data.clear();
        parseByTag(danmu, xml);
        return danmu;
    }

    private static boolean parseByXmlPull(Danmu danmu, String xml) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                    String param = parser.getAttributeValue(null, "p");
                    String text = parser.nextText();
                    if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)) {
                        danmu.data.add(new Data(param, text));
                    }
                }
                eventType = parser.next();
            }
            return !danmu.data.isEmpty();
        } catch (Throwable th) {
            return false;
        }
    }

    private static void parseByTag(Danmu danmu, String xml) {
        Matcher matcher = D_TAG_PATTERN.matcher(xml);
        while (matcher.find()) {
            String param = decodeXmlString(matcher.group(2));
            String text = matcher.group(3);
            if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)) {
                danmu.data.add(new Data(param, text));
            }
        }
    }

    private static String escapeIllegalEntities(String xml) {
        StringBuilder builder = new StringBuilder(xml.length());
        int length = xml.length();
        for (int i = 0; i < length; i++) {
            char ch = xml.charAt(i);
            if (ch == '&' && !isLegalEntity(xml, i + 1)) {
                builder.append("&amp;");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean isLegalEntity(String text, int start) {
        int end = text.indexOf(';', start);
        if (end < 0 || end - start > 10) return false;
        String entity = text.substring(start, end);
        if ("amp".equals(entity) || "lt".equals(entity) || "gt".equals(entity)
                || "quot".equals(entity) || "apos".equals(entity)) {
            return true;
        }
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return isHexNumber(entity, 2);
        }
        return entity.startsWith("#") && isDecimalNumber(entity, 1);
    }

    private static boolean isDecimalNumber(String text, int start) {
        if (text.length() <= start) return false;
        for (int i = start; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexNumber(String text, int start) {
        if (text.length() <= start) return false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isDigit(ch)
                    && (ch < 'a' || ch > 'f')
                    && (ch < 'A' || ch > 'F')) {
                return false;
            }
        }
        return true;
    }

    private static String decodeXmlString(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
    }

    public List<Data> getData() {
        return data.isEmpty() ? Collections.emptyList() : data;
    }

    public static class Data {
        private final String param;
        private final String text;

        Data(String param, String text) {
            this.param = param;
            this.text = text;
        }

        public String getParam() {
            return TextUtils.isEmpty(param) ? "" : param;
        }

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }
}
