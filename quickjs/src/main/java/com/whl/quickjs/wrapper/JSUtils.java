package com.whl.quickjs.wrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        JSArray array = ctx.createNewJSArray();
        if (items == null || items.isEmpty()) return array;
        for (int i = 0; i < items.size(); i++) array.set(toJSValue(ctx, items.get(i)), i);
        return array;
    }

    public JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createNewJSArray();
        if (bytes == null || bytes.length == 0) return array;
        for (int i = 0; i < bytes.length; i++) array.set((int) bytes[i], i);
        return array;
    }

    public JSArray toArray(QuickJSContext ctx, T[] arrays) {
        JSArray array = ctx.createNewJSArray();
        if (arrays == null || arrays.length == 0) return array;
        for (int i = 0; i < arrays.length; i++) array.set(toJSValue(ctx, arrays[i]), i);
        return array;
    }

    public JSObject toObj(QuickJSContext ctx, Map<?, ?> map) {
        JSObject obj = ctx.createNewJSObject();
        if (map == null || map.isEmpty()) return obj;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) continue;
            ctx.setProperty(obj, String.valueOf(key), toJSValue(ctx, entry.getValue()));
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
            JSArray array = ctx.createNewJSArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.set(toJSValue(ctx, Array.get(value, i)), i);
            }
            return array;
        }
        return value;
    }

    public static JSONObject toJsonObject(JSObject object) {
        if (object == null) return new JSONObject();
        try {
            return new JSONObject(object.stringify());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static JSONArray toJsonArray(JSArray array) {
        if (array == null) return new JSONArray();
        try {
            return new JSONArray(array.stringify());
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
