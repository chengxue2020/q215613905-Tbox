package com.github.tvbox.osc.util;

import android.graphics.Color;

import com.orhanobut.hawk.Hawk;

public class DanmuHelper {
    private static final String[] PALETTE = new String[]{
            "#ffffff", "#70f3ff", "#44cef6", "#3eede7", "#00e079", "#2edfa3",
            "#bce672", "#fff143", "#ffa631", "#ff7500", "#ff4e20", "#ff2d51",
            "#ef7a82", "#ff0097", "#b0a4e3", "#4b5cc4"
    };

    public static boolean isOpen() {
        return Hawk.get(HawkConfig.DANMU_OPEN, true);
    }

    public static void setOpen(boolean open) {
        Hawk.put(HawkConfig.DANMU_OPEN, open);
    }

    public static int getMaxLine() {
        return Hawk.get(HawkConfig.DANMU_MAX_LINE, 3);
    }

    public static void setMaxLine(int line) {
        Hawk.put(HawkConfig.DANMU_MAX_LINE, clamp(line, 1, 15));
    }

    public static float getSpeed() {
        return Hawk.get(HawkConfig.DANMU_SPEED, 1.5f);
    }

    public static void setSpeed(float speed) {
        Hawk.put(HawkConfig.DANMU_SPEED, speed);
    }

    public static float getAlpha() {
        return Hawk.get(HawkConfig.DANMU_ALPHA, 0.9f);
    }

    public static void setAlpha(float alpha) {
        Hawk.put(HawkConfig.DANMU_ALPHA, Math.max(0.1f, Math.min(alpha, 1.0f)));
    }

    public static float getSizeScale() {
        return Hawk.get(HawkConfig.DANMU_SIZE_SCALE, 0.8f);
    }

    public static void setSizeScale(float scale) {
        Hawk.put(HawkConfig.DANMU_SIZE_SCALE, Math.max(0.6f, Math.min(scale, 2.0f)));
    }

    public static boolean useRandomColor() {
        return Hawk.get(HawkConfig.DANMU_RANDOM_COLOR, false);
    }

    public static void setRandomColor(boolean randomColor) {
        Hawk.put(HawkConfig.DANMU_RANDOM_COLOR, randomColor);
    }

    public static int randomColor() {
        int index = (int) (Math.random() * PALETTE.length);
        return Color.parseColor(PALETTE[index]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
