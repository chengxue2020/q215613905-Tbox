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
        JSObject obj = ctx.createJSObject();
        if (map == null || map.isEmpty()) return obj;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) continue;
            obj.set(String.valueOf(key), toJSValue(ctx, entry.getValue()));
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
            JSArray array = ctx.createJSArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.push(toJSValue(ctx, Array.get(value, i)));
            }
            return array;
        }
        return value;
    }

    static JSArray toArray(QuickJSContext ctx, List<?> list) {
        JSArray array = ctx.createJSArray();
        if (list == null || list.isEmpty()) return array;
        for (int i = 0; i < list.size(); i++) {
            array.push(toJSValue(ctx, list.get(i)));
        }
        return array;
    }

    static JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createJSArray();
        if (bytes == null || bytes.length == 0) return array;
        for (byte b : bytes) {
            array.push((int) b);
        }
        return array;
    }
}
