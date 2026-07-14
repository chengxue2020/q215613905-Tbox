package com.whl.quickjs.wrapper;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MapCreator {

    default Map createMap() {
        return new HashMap();
    }

    default Map create() {
        return createMap();
    }

    default Map toMap() {
        return createMap();
    }

    static JSObject create(QuickJSContext ctx, Map<?, ?> map) {
        return toObj(ctx, map);
    }

    static JSObject createJSObject(QuickJSContext ctx, Map<?, ?> map) {
        return toObj(ctx, map);
    }

    static JSObject toObj(QuickJSContext ctx, Map<?, ?> map) {
        JSObject obj = ctx.createNewJSObject();
        if (map == null || map.isEmpty()) return obj;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) continue;
            ctx.setProperty(obj, String.valueOf(key), toJSValue(ctx, entry.getValue()));
        }
        return obj;
    }

    static JSObject toObject(QuickJSContext ctx, Map<?, ?> map) {
        return toObj(ctx, map);
    }

    static Object toJSValue(QuickJSContext ctx, Object value) {
        if (value == null) return null;
        if (value instanceof JSObject || value instanceof JSCallFunction) return value;
        if (value instanceof Map) return toObj(ctx, (Map<?, ?>) value);
        if (value instanceof List) return toArray(ctx, (List<?>) value);
        if (value instanceof byte[]) return toArray(ctx, (byte[]) value);
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            JSArray array = ctx.createNewJSArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.set(toJSValue(ctx, Array.get(value, i)), i);
            }
            return array;
        }
        return value;
    }

    static JSArray toArray(QuickJSContext ctx, List<?> list) {
        JSArray array = ctx.createNewJSArray();
        if (list == null || list.isEmpty()) return array;
        for (int i = 0; i < list.size(); i++) {
            array.set(toJSValue(ctx, list.get(i)), i);
        }
        return array;
    }

    static JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createNewJSArray();
        if (bytes == null || bytes.length == 0) return array;
        for (int i = 0; i < bytes.length; i++) {
            array.set((int) bytes[i], i);
        }
        return array;
    }
}
