package com.leo618.hellome.libcore.util;


import com.leo618.hellome.libcore.common.Configs;

/**
 * function : 日志输出.
 * <p></p>
 * Created by lzj on 2015/12/31.
 */
@SuppressWarnings("unused")
public final class Logg {
    private static boolean DEBUG = Configs.DEBUG;

    public static void v(String tag, String content) {
        if (!DEBUG) return;
        android.util.Log.v(tag, content);
    }

    public static void v(final String tag, Object... objs) {
        if (!DEBUG) return;
        android.util.Log.v(tag, getInfo(objs));
    }

    public static void w(String tag, String content) {
        if (!DEBUG) return;
        android.util.Log.w(tag, content);
    }

    public static void w(final String tag, Object... objs) {
        if (!DEBUG) return;
        android.util.Log.w(tag, getInfo(objs));
    }

    public static void i(String tag, String content) {
        if (!DEBUG) return;
        android.util.Log.i(tag, content);
    }

    public static void i(final String tag, Object... objs) {
        if (!DEBUG) return;
        android.util.Log.i(tag, getInfo(objs));
    }

    public static void d(String tag, String content) {
        if (!DEBUG) return;
        android.util.Log.d(tag, content);
    }

    public static void d(final String tag, Object... objs) {
        if (!DEBUG) return;
        android.util.Log.d(tag, getInfo(objs));
    }

    public static void e(String tag, String content) {
        if (!DEBUG) return;
        android.util.Log.e(tag, content);
    }

    public static void e(String tag, String content, Throwable e) {
        if (!DEBUG) return;
        android.util.Log.e(tag, content, e);
    }

    public static void e(final String tag, Object... objs) {
        if (!DEBUG) return;
        android.util.Log.e(tag, getInfo(objs));
    }

    private static String getInfo(Object... objs) {
        StringBuilder sb = new StringBuilder();
        if (null == objs) {
            sb.append("no mesage.");
        } else {
            for (Object object : objs) {
                sb.append("--");
                sb.append(object);
            }
        }
        return sb.toString();
    }

    public static void sysOut(Object msg) {
        if (!DEBUG) return;
        System.out.println(msg);
    }

    public static void sysErr(Object msg) {
        if (!DEBUG) return;
        System.err.println(msg);
    }
}
