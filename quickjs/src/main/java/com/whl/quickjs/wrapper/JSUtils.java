package com.whl.quickjs.wrapper;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JSUtils<T> {

    public static boolean isEmpty(Object obj) {
        if (obj == null) return true;
        else if (obj instanceof CharSequence) return ((CharSequence) obj).length() == 0;
        else if (obj instanceof Collection) return ((Collection) obj).isEmpty();
        else if (obj instanceof Map) return ((Map) obj).isEmpty();
        else if (obj.getClass().isArray()) return Array.getLength(obj) == 0;

        return false;
    }

    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    public JSArray toArray(QuickJSContext ctx, List<T> items) {
        JSArray array = ctx.createJSArray();
        if (items == null || items.isEmpty()) return array;
        for (int i = 0; i < items.size(); i++) array.push(toJSValue(ctx, items.get(i)));
        return array;
    }

    public JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createJSArray();
        if (bytes == null || bytes.length == 0) return array;
        for (byte aByte : bytes) array.push((int) aByte);
        return array;
    }

    public JSArray toArray(QuickJSContext ctx, T[] arrays) {
        JSArray array = ctx.createJSArray();
        if (arrays == null || arrays.length == 0) return array;
        for (T t : arrays) {
            array.push(toJSValue(ctx, t));
        }
        return array;
    }

    public JSObject toObj(QuickJSContext ctx, Map<?, ?> map) {
        JSObject obj = ctx.createJSObject();
        if (map == null || map.isEmpty()) return obj;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) continue;
            obj.set(String.valueOf(key), toJSValue(ctx, entry.getValue()));
        }
        return obj;
    }

    public Object toJSValue(QuickJSContext ctx, Object value) {
        if (value == null) return null;
        if (value instanceof JSObject || value instanceof JSCallFunction) return value;
        if (value instanceof Map) return toObj(ctx, (Map<?, ?>) value);
        if (value instanceof List) return toArray(ctx, (List) value);
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
}
