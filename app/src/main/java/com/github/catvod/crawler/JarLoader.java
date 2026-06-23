package com.github.catvod.crawler;

import android.content.Context;
import android.util.Log;


import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.MD5;
import com.lzy.okgo.OkGo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JarLoader {
    private final ConcurrentHashMap<String, DexClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> proxyMethods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> siteJarKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> protectedInitJars = new ConcurrentHashMap<>();
    private volatile String recentJarKey = "";
    private static final String DEFAULT_CONFIG_JSON = "{\"version\":\"25.0\",\"update\":\"关闭\",\"danmuColor\":\"默认\",\"aliHD\":\"阿里原画\",\"quarkHD\":\"夸克原画\",\"ucHD\":\"UC原画\",\"panBlock\":\"\",\"proxyMode\":\"Go多线程\",\"pansouUrl\":\"https://so.252035.xyz\",\"panOrder\":\"百度,夸克,UC,天翼,123,阿里,移动\",\"homePage\":\"热门电影,热播剧集,热门动漫,热播综艺,电影筛选,电视筛选,电影榜单,电视剧榜单\",\"aliThread\":\"64\",\"quarkThread\":\"16\",\"ucThread\":\"自动\"}";

    /**
     * 不要在主线程调用我
     *
     * @param cache
     */
    public boolean load(String cache) {
        recentJarKey = "main";
        return loadClassLoader(cache, recentJarKey);
    }

    public void setRecentJarKey(String key) {
        if (key != null && !key.isEmpty()) {
            recentJarKey = key;
            injectProxyPort(classLoaders.get(key));
        }
    }

    public void loadLiveJar(String jarUrl) {
        String[] urls = jarUrl.split(";md5;");
        jarUrl = urls[0];
        String jarKey = MD5.string2MD5(jarUrl);
        String jarMd5 = urls.length > 1 ? urls[1].trim() : "";
        loadJarInternal(jarUrl,jarMd5,jarKey);
        recentJarKey = jarKey;
    }

    public void clear() {
        spiders.clear();
        proxyMethods.clear();
        classLoaders.clear();
        siteJarKeys.clear();
    }

    private boolean loadClassLoader(String jar, String key) {
        if (classLoaders.containsKey(key)){
            injectProxyPort(classLoaders.get(key));
            Log.i("JarLoader", "echo-loadClassLoader jar缓存: " + key);
            ensureDefaultConfig();
            return true;
        }
        boolean success = false;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_csp");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            final DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            injectProxyPort(classLoader);
            injectProtectedClassLoader(classLoader);
            int count = 0;
            do {
                try {
                    final Class<?> classInit = classLoader.loadClass("com.github.catvod.spider.Init");
                    if (classInit != null) {
                        final Method initMethod = classInit.getMethod("init", Context.class);
                        // 在子线程中调用 init 方法，避免网络请求在主线程中执行
                        Thread initThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (isProtectedInitJar(jar)) {
                                        initProtectedJar(classInit);
                                    } else {
                                        initMethod.invoke(null, App.getInstance());
                                        invokeSaveConfig(classInit);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    ensureDefaultConfig();
                                }
                            }
                        });
                        initThread.start();
                        initThread.join();
                        Log.i("JarLoader", "echo-自定义爬虫代码加载成功!");
                        success = true;
                        try {
                            Class<?> proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                            Method proxyMethod = proxy.getMethod("proxy", Map.class);
                            proxyMethods.put(key, proxyMethod);
                        } catch (Throwable th) {
                            // 可以记录错误日志
                            th.printStackTrace();
                        }
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 2);

            if (success) {
                classLoaders.put(key, classLoader);
            }
        } catch (Throwable th) {
            Log.i("JarLoader", "echo-loadClassLoader error key=" + key + ", msg=" + th.getMessage());
            th.printStackTrace();
        }
        return success;
    }

    private boolean isProtectedInitJar(String jar) {
        Boolean cached = protectedInitJars.get(jar);
        if (cached != null) return cached;
        boolean result = false;
        try {
            File file = new File(jar);
            if (file.exists()) {
                byte[] data = FileUtils.readSimple(file);
                if (data != null && data.length > 4 && data[0] == 'd' && data[1] == 'e' && data[2] == 'x') {
                    result = isProtectedInitDex(data);
                } else {
                    try (ZipFile zip = new ZipFile(file)) {
                        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            if (!entry.getName().endsWith(".dex")) continue;
                            try (InputStream is = zip.getInputStream(entry)) {
                                if (isProtectedInitDex(readBytes(is))) {
                                    result = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        protectedInitJars.put(jar, result);
        return result;
    }

    private boolean isProtectedInitDex(byte[] data) {
        if (data == null || data.length == 0) return false;
        try {
            String text = new String(data, "UTF-8");
            return text.contains("包名不匹配")
                    && text.contains("killProcess")
                    && !text.contains("com.github.tvbox.osc.jun");
        } catch (Throwable th) {
            return false;
        }
    }

    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private void initProtectedJar(Class<?> classInit) {
        try {
            Method get = classInit.getMethod("get");
            Object init = get.invoke(null);
            Field context = classInit.getDeclaredField("c");
            context.setAccessible(true);
            context.set(init, App.getInstance());
        } catch (Throwable ignored) {
        }
        invokeSaveConfig(classInit);
        invokeNoArg(classInit, "replaceCloudDiskNames");
        invokeStartGoProxy(classInit);
    }

    private void injectProtectedClassLoader(DexClassLoader classLoader) {
        if (classLoader == null) return;
        injectClassLoaderFields(classLoader, "com.github.catvod.spider.Init");
        injectClassLoaderFields(classLoader, "com.github.catvod.spider.DexNative");
        injectClassLoaderFields(classLoader, "com.github.catvod.spider.BaseSpiderGuard");
    }

    private void injectClassLoaderFields(DexClassLoader classLoader, String className) {
        try {
            Class<?> cls = classLoader.loadClass(className);
            setClassLoaderFields(cls, null, classLoader);
            Object singleton = getSingleton(cls);
            if (singleton != null) {
                setClassLoaderFields(cls, singleton, classLoader);
            }
        } catch (Throwable ignored) {
        }
    }

    private Object getSingleton(Class<?> cls) {
        try {
            Method get = cls.getMethod("get");
            return get.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void setClassLoaderFields(Class<?> cls, Object instance, DexClassLoader classLoader) {
        try {
            for (Field field : cls.getDeclaredFields()) {
                if (!ClassLoader.class.isAssignableFrom(field.getType()) && field.getType() != Object.class) continue;
                boolean isStatic = Modifier.isStatic(field.getModifiers());
                if (instance == null && !isStatic) continue;
                if (instance != null && isStatic) continue;
                field.setAccessible(true);
                Object current = field.get(instance);
                if (current == null) {
                    field.set(instance, classLoader);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void invokeSaveConfig(Class<?> classInit) {
        try {
            Method saveConfig = classInit.getMethod("saveConfig");
            saveConfig.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private void invokeNoArg(Class<?> classInit, String methodName) {
        try {
            Method method = classInit.getMethod(methodName);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private void invokeStartGoProxy(Class<?> classInit) {
        try {
            Method method = classInit.getMethod("startGoProxy", Context.class);
            method.invoke(null, App.getInstance());
        } catch (Throwable ignored) {
        }
    }

    private void injectProxyPort(DexClassLoader classLoader) {
        com.github.catvod.Proxy.set(getServerPort());
        if (classLoader == null) return;
        try {
            Class<?> proxy = classLoader.loadClass("com.github.catvod.Proxy");
            Method set = proxy.getMethod("set", int.class);
            set.invoke(null, getServerPort());
        } catch (Throwable ignored) {
        }
    }

    private int getServerPort() {
        try {
            String address = ControlManager.get().getAddress(true);
            if (address != null && address.startsWith("http://127.0.0.1:")) {
                String baseUrl = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
                return Integer.parseInt(baseUrl.substring(baseUrl.lastIndexOf(":") + 1));
            }
        } catch (Throwable ignored) {
        }
        return RemoteServer.serverPort;
    }

    private void ensureDefaultConfig() {
        try {
            File config = new File(App.getInstance().getFilesDir(), "config.json");
            boolean rewrite = !config.exists() || config.length() == 0;
            if (!rewrite) {
                try {
                    JSONObject obj = new JSONObject(FileUtils.readFileToString(config.getAbsolutePath(), "UTF-8"));
                    rewrite = !obj.has("homePage");
                } catch (Throwable th) {
                    rewrite = true;
                }
            }
            if (rewrite) {
                FileUtils.saveCache(config, DEFAULT_CONFIG_JSON);
            }
        } catch (Throwable ignored) {
        }
    }

    private DexClassLoader loadJarInternal(String jar, String md5, String key) {
        if (classLoaders.containsKey(key)){
            Log.i("JarLoader", "echo-loadJarInternal jar缓存: " + key);
            ensureDefaultConfig();
            return classLoaders.get(key);
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + key + ".jar");
        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classLoaders.get(key);
                }else {
                    return null;
                }
            }
        }else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classLoaders.get(key);
                }
            }
        }
        try {
            Response response = OkGo.<File>get(jar).execute();
            assert response.body() != null;
            InputStream is = response.body().byteStream();
            OutputStream os = new FileOutputStream(cache);
            try {
                byte[] buffer = new byte[2048];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            loadClassLoader(cache.getAbsolutePath(), key);
            return classLoaders.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Spider getSpider(String key, String cls, String ext, String jar) {
        if (spiders.containsKey(key)) {
            String jarKey = getJarKey(jar);
            recentJarKey = jarKey;
            siteJarKeys.put(key, jarKey);
            injectProxyPort(classLoaders.get(jarKey));
            Log.i("JarLoader", "echo-getSpider spider缓存: " + key);
            ensureDefaultConfig();
            return spiders.get(key);
        }
        String clsKey = cls.replace("csp_", "");
        String jarUrl = "";
        String jarMd5 = "";
        String jarKey;
        if (jar.isEmpty()) {
            jarKey = "main";
        } else {
            String[] urls = jar.split(";md5;");
            jarUrl = urls[0];
            jarKey = MD5.string2MD5(jarUrl);
            jarMd5 = urls.length > 1 ? urls[1].trim() : "";
        }
        recentJarKey = jarKey;
        siteJarKeys.put(key, jarKey);
        assert jarKey != null;
        DexClassLoader classLoader = jarKey.equals("main")? classLoaders.get("main"):loadJarInternal(jarUrl, jarMd5, jarKey);
        if (classLoader == null) {
            Log.i("JarLoader", "echo-getSpider classLoader null key=" + key + ", jarKey=" + jarKey + ", jarUrl=" + jarUrl);
            return new SpiderNull();
        }
        try {
            ensureDefaultConfig();
            Log.i("JarLoader", "echo-getSpider 加载spider: " + key);
            injectProtectedClassLoader(classLoader);
            Spider sp = (Spider) classLoader.loadClass("com.github.catvod.spider." + clsKey).newInstance();
            sp.siteKey = key;
            sp.initApi(new SpiderApi());
            sp.init(App.getInstance(), ext);
//            if (!jar.isEmpty()) {
//                sp.homeContent(false); // 增加此行 应该可以解决部分写的有问题源的历史记录问题 但会增加这个源的首次加载时间 不需要可以已删掉
//            }
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            Log.i("JarLoader", "echo-getSpider error key=" + key + ", clsKey=" + clsKey + ", msg=" + th.getMessage());
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    private String getJarKey(String jar) {
        if (jar == null || jar.isEmpty()) {
            return "main";
        }
        String[] urls = jar.split(";md5;");
        return MD5.string2MD5(urls[0]);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Json" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            assert classLoader != null;
            Class<?> jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Mix" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            assert classLoader != null;
            Class<?> jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, name, flag, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public Object[] proxyInvoke(Map<String,String> params) {
        String jarKey = getProxyJarKey(params);
        injectProxyPort(classLoaders.get(jarKey));
        return invokeProxy(proxyMethods.get(jarKey), params);
    }

    private String getProxyJarKey(Map<String, String> params) {
        String siteKey = params == null ? null : params.get("siteKey");
        if (siteKey != null && siteJarKeys.containsKey(siteKey)) {
            return siteJarKeys.get(siteKey);
        }
        return recentJarKey;
    }

    private Object[] invokeProxy(Method proxyFun, Map<String, String> params) {
        if (proxyFun == null) return null;
        try {
            return (Object[]) proxyFun.invoke(null, params);
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }
}
