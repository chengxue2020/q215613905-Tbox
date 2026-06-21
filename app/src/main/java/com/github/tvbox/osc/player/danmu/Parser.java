package com.github.tvbox.osc.player.danmu;

import android.graphics.Color;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.Danmu;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.lzy.okgo.OkGo;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import master.flame.danmaku.danmaku.model.AlphaValue;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Duration;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.SpecialDanmaku;
import master.flame.danmaku.danmaku.model.android.DanmakuFactory;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.util.DanmakuUtils;

public class Parser extends BaseDanmakuParser {
    private final Danmu danmu;
    private float scaleX;
    private float scaleY;
    private int index;

    public Parser(String input) {
        this.danmu = Danmu.fromXml(resolveContent(input));
    }

    public int getDanmuCount() {
        return danmu.getData().size();
    }

    private String resolveContent(String input) {
        if (TextUtils.isEmpty(input)) return "";
        String source = input.trim();
        if (source.startsWith("file")) return FileUtils.read(source);
        if (source.startsWith("http")) {
            try {
                okhttp3.Response response = OkGo.<String>get(source).execute();
                String content = readBody(response);
                LOG.i("echo-danmu http code: " + response.code()
                        + ", encoding: " + response.header("Content-Encoding", "")
                        + ", length: " + content.length());
                return content;
            } catch (Throwable th) {
                LOG.e("echo-danmu load error: " + th.getMessage());
                return "";
            }
        }
        return source;
    }

    private String readBody(okhttp3.Response response) throws IOException {
        if (response.body() == null) return "";
        byte[] bytes = response.body().bytes();
        if (looksXml(bytes)) return new String(bytes, StandardCharsets.UTF_8);
        String encoding = response.header("Content-Encoding", "");
        if ("gzip".equalsIgnoreCase(encoding)) {
            bytes = readAll(new GZIPInputStream(new ByteArrayInputStream(bytes)));
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            bytes = inflate(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean looksXml(byte[] bytes) {
        if (bytes == null) return false;
        for (byte value : bytes) {
            char ch = (char) (value & 0xff);
            if (Character.isWhitespace(ch)) continue;
            return ch == '<';
        }
        return false;
    }

    private byte[] inflate(byte[] bytes) throws IOException {
        try {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            Inflater inflater = new Inflater(true);
            try {
                return readAll(new InflaterInputStream(new ByteArrayInputStream(bytes), inflater));
            } finally {
                inflater.end();
            }
        }
    }

    private byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    @Override
    protected Danmakus parse() {
        Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
        for (Danmu.Data data : danmu.getData()) {
            BaseDanmaku danmaku = createDanmaku(data);
            if (danmaku == null) continue;
            synchronized (result.obtainSynchronizer()) {
                result.addItem(danmaku);
            }
        }
        return result;
    }

    @Override
    public BaseDanmakuParser setDisplayer(IDisplayer display) {
        super.setDisplayer(display);
        scaleX = mDispWidth / DanmakuFactory.BILI_PLAYER_WIDTH;
        scaleY = mDispHeight / DanmakuFactory.BILI_PLAYER_HEIGHT;
        return this;
    }

    private BaseDanmaku createDanmaku(Danmu.Data data) {
        try {
            String[] values = data.getParam().split(",");
            if (values.length < 4) return null;
            int type = Integer.parseInt(values[1]);
            BaseDanmaku item = mContext.mDanmakuFactory.createDanmaku(type, mContext);
            if (item == null) return null;
            long time = (long) (Float.parseFloat(values[0]) * 1000);
            float size = Float.parseFloat(values[2]) * Math.max(1.0f, mDispDensity - 0.6f);
            int color = DanmuHelper.useRandomColor()
                    ? DanmuHelper.randomColor()
                    : (int) ((0x00000000ff000000L | Long.parseLong(values[3])) & 0x00000000ffffffffL);
            item.setTime(time);
            item.setTimer(mTimer);
            item.textSize = size;
            item.textColor = color;
            item.textShadowColor = color <= Color.BLACK ? Color.WHITE : Color.BLACK;
            item.flags = mContext.mGlobalFlagValues;
            item.index = index++;
            DanmakuUtils.fillText(item, decodeXmlString(data.getText()));
            if (item.getType() == BaseDanmaku.TYPE_SPECIAL
                    && data.getText().startsWith("[")
                    && data.getText().endsWith("]")
                    && !setSpecial(item)) {
                return null;
            }
            return item;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean setSpecial(BaseDanmaku item) {
        String[] textArr;
        try {
            JSONArray jsonArray = new JSONArray(item.text);
            textArr = new String[jsonArray.length()];
            for (int i = 0; i < textArr.length; i++) {
                textArr[i] = jsonArray.getString(i);
            }
        } catch (JSONException e) {
            return false;
        }
        if (textArr.length < 5 || TextUtils.isEmpty(textArr[4])) return false;
        try {
            DanmakuUtils.fillText(item, textArr[4]);
            float beginX = Float.parseFloat(textArr[0]);
            float beginY = Float.parseFloat(textArr[1]);
            float endX = beginX;
            float endY = beginY;
            String[] alphaArr = textArr[2].split("-");
            int beginAlpha = (int) (AlphaValue.MAX * Float.parseFloat(alphaArr[0]));
            int endAlpha = alphaArr.length > 1
                    ? (int) (AlphaValue.MAX * Float.parseFloat(alphaArr[1]))
                    : beginAlpha;
            long alphaDuration = (long) (Float.parseFloat(textArr[3]) * 1000);
            long translationDuration = alphaDuration;
            long translationStartDelay = 0;
            float rotateY = 0;
            float rotateZ = 0;
            if (textArr.length >= 7) {
                rotateZ = Float.parseFloat(textArr[5]);
                rotateY = Float.parseFloat(textArr[6]);
            }
            if (textArr.length >= 11) {
                endX = Float.parseFloat(textArr[7]);
                endY = Float.parseFloat(textArr[8]);
                if (!TextUtils.isEmpty(textArr[9])) translationDuration = Long.parseLong(textArr[9]);
                if (!TextUtils.isEmpty(textArr[10])) translationStartDelay = (long) Float.parseFloat(textArr[10]);
            }
            if (isPercentageNumber(textArr[0])) beginX *= DanmakuFactory.BILI_PLAYER_WIDTH;
            if (isPercentageNumber(textArr[1])) beginY *= DanmakuFactory.BILI_PLAYER_HEIGHT;
            if (textArr.length >= 8 && isPercentageNumber(textArr[7])) endX *= DanmakuFactory.BILI_PLAYER_WIDTH;
            if (textArr.length >= 9 && isPercentageNumber(textArr[8])) endY *= DanmakuFactory.BILI_PLAYER_HEIGHT;
            item.duration = new Duration(alphaDuration);
            item.rotationZ = rotateZ;
            item.rotationY = rotateY;
            mContext.mDanmakuFactory.fillTranslationData(item, beginX, beginY, endX, endY,
                    translationDuration, translationStartDelay, scaleX, scaleY);
            mContext.mDanmakuFactory.fillAlphaData(item, beginAlpha, endAlpha, alphaDuration);
            if (textArr.length >= 12 && "true".equalsIgnoreCase(textArr[11])) {
                item.textShadowColor = Color.TRANSPARENT;
            }
            if (textArr.length >= 14) {
                ((SpecialDanmaku) item).isQuadraticEaseOut = "0".equals(textArr[13]);
            }
            if (textArr.length >= 15 && !TextUtils.isEmpty(textArr[14])) {
                fillLinePath(item, textArr[14]);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void fillLinePath(BaseDanmaku item, String motionPathString) {
        String motionPath = motionPathString.substring(1);
        if (TextUtils.isEmpty(motionPath)) return;
        String[] pointStrArray = motionPath.split("L");
        float[][] points = new float[pointStrArray.length][2];
        for (int i = 0; i < pointStrArray.length; i++) {
            String[] pointArray = pointStrArray[i].split(",");
            if (pointArray.length < 2) return;
            points[i][0] = Float.parseFloat(pointArray[0]);
            points[i][1] = Float.parseFloat(pointArray[1]);
        }
        DanmakuFactory.fillLinePathData(item, points, scaleX, scaleY);
    }

    private boolean isPercentageNumber(String number) {
        return number != null && number.contains(".");
    }

    private String decodeXmlString(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&gt;", ">")
                .replace("&lt;", "<");
    }
}
