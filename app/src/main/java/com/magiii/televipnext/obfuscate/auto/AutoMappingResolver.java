package com.magiii.televipnext.obfuscate.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.struct.ClientObfuscationData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AutoMappingResolver {

    private static final String PREFS_NAME = "TeleVip_AutoMap";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_SCHEMA_VER = "schemaVersion";
    private static final String KEY_MAPPING_JSON = "mappingJson";
    private static final int SCHEMA_VERSION = 72;

    private final Map<String, String> classMap = new HashMap<>();
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();

    public static ClientObfuscationData resolveOrLoadCache(Context context, String sourceDir, ClassLoader classLoader) {
        if (context == null || sourceDir == null) return null;

        long versionCode = getVersionCode(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (versionCode > 0
                && prefs.getInt(KEY_SCHEMA_VER, 0) == SCHEMA_VERSION
                && prefs.getLong(KEY_VERSION_CODE, -1L) == versionCode) {
            String cached = prefs.getString(KEY_MAPPING_JSON, null);
            if (cached != null && !cached.isEmpty()) {
            ClientObfuscationData data = ClientObfuscationData.parse(cached, "{}");
            if (data != null) {
                Logger.w("AutoMappingResolver: loaded cached mapping for versionCode=" + versionCode);
                return data;
            }
        }
        }

        long t0 = System.currentTimeMillis();
        try {
            AutoMappingResolver resolver = new AutoMappingResolver();
            String json = resolver.buildMapping(sourceDir, classLoader);
            if (json != null) {
                prefs.edit()
                        .putLong(KEY_VERSION_CODE, versionCode)
                        .putInt(KEY_SCHEMA_VER, SCHEMA_VERSION)
                        .putString(KEY_MAPPING_JSON, json)
                        .apply();
                Logger.w("AutoMappingResolver: resolved " + resolver.classMap.size() + " classes, "
                        + resolver.methodMap.size() + " methods, " + resolver.fieldMap.size()
                        + " fields in " + (System.currentTimeMillis() - t0) + "ms (v=" + versionCode + ")");
                return ClientObfuscationData.parse(json, "{}");
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return null;
    }

    private static long getVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Throwable t) {
            return -1L;
        }
    }


    private String buildMapping(String sourceDir, ClassLoader classLoader) throws Exception {
        DexScanner scanner = new DexScanner(sourceDir);
        MappingContext ctx = new MappingContext(classMap, fieldMap, methodMap);
        CoreMappingResolver.resolveAll(ctx, scanner, classLoader);

        // Serialize to JSON
        JSONObject root = new JSONObject();
        JSONArray clsArr = new JSONArray();
        for (Map.Entry<String, String> e : classMap.entrySet()) {
            JSONObject o = new JSONObject();
            o.put("o", e.getKey());
            o.put("r", e.getValue());
            clsArr.put(o);
        }
        JSONArray fldArr = new JSONArray();
        for (Map.Entry<String, String> e : fieldMap.entrySet()) {
            String[] parts = e.getKey().split("#", 2);
            JSONObject o = new JSONObject();
            o.put("c", parts[0]);
            o.put("o", parts[1]);
            o.put("r", e.getValue());
            fldArr.put(o);
        }
        JSONArray mthArr = new JSONArray();
        for (Map.Entry<String, String> e : methodMap.entrySet()) {
            String[] parts = e.getKey().split("#", 2);
            JSONObject o = new JSONObject();
            o.put("c", parts[0]);
            o.put("o", parts[1]);
            o.put("r", e.getValue());
            mthArr.put(o);
        }

        root.put("classes", clsArr);
        root.put("fields", fldArr);
        root.put("methods", mthArr);
        return root.toString();
    }
}
