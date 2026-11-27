package com.magiii.televipnext.obfuscate;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.obfuscate.auto.AutoMappingResolver;
import com.magiii.televipnext.obfuscate.struct.ClientObfuscationData;
import com.magiii.televipnext.utils.JsonAssetReader;
import com.magiii.televipnext.utils.Utils;

import java.util.EnumMap;
import java.util.Map;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ObfuscationManager {

    private static final Map<ClientManager.Client, ClientObfuscationData> cache =
            new EnumMap<>(ClientManager.Client.class);

    private static volatile ClientObfuscationData currentClientData;
    private static volatile boolean attemptedLoad = false;

    public static void initRuntimeResolver() {
        if (currentClientData != null) return;
        synchronized (ObfuscationManager.class) {
            if (currentClientData != null) return;
            attemptedLoad = false;
        }
        current();
    }

    public static ClientObfuscationData current() {
        if (currentClientData != null) return currentClientData;
        if (attemptedLoad) return null;

        synchronized (ObfuscationManager.class) {
            if (currentClientData != null) return currentClientData;
            if (attemptedLoad) return null;

            ClientManager.Client type = ClientManager.getCurrent();
            if (type != null) {
                ClientObfuscationData staticData = load(type);
                if (isStaticMappingValid(staticData, Utils.classLoader)) {
                    currentClientData = staticData;
                }
            }

            if (currentClientData != null) {
                attemptedLoad = true;
                return currentClientData;
            }

            // Defer runtime auto-resolution until LaunchActivity / Context is available
            if (Utils.getCurrentActivity() == null || Utils.sourceDir == null) {
                return null;
            }

            attemptedLoad = true;
            // Scan DEX if the client is fully or partially obfuscated
            if (needsAutoMapping(Utils.classLoader)) {
                currentClientData = AutoMappingResolver.resolveOrLoadCache(
                        Utils.getCurrentActivity(),
                        Utils.sourceDir,
                        Utils.classLoader
                );
            }
            return currentClientData;
        }
    }

    private static boolean isStaticMappingValid(ClientObfuscationData data, ClassLoader cl) {
        if (data == null || cl == null) return false;
        String resolvedMc = data.resolveClass(ClassNames.MESSAGES_CONTROLLER);
        return resolvedMc != null && ReflectHelper.findClassIfExists(resolvedMc, cl) != null;
    }

    private static boolean needsAutoMapping(ClassLoader cl) {
        if (ReflectHelper.findClassIfExists(ClassNames.MESSAGES_CONTROLLER, cl) == null) return true;
        if (ReflectHelper.findClassIfExists(ClassNames.CHAT_ACTIVITY, cl) == null) return true;
        if (ReflectHelper.findClassIfExists(ClassNames.CHAT_MESSAGE_CELL, cl) == null) return true;
        if (ReflectHelper.findClassIfExists(ClassNames.LONG_SPARES_ARRAY, cl) == null) return true;
        boolean hasFactory = ReflectHelper.findClassIfExists("org.telegram.ui.SettingsActivity$SettingCell$Factory", cl) != null;
        boolean hasDrawer = ReflectHelper.findClassIfExists(ClassNames.DRAWER_LAYOUT_ADAPTER, cl) != null;
        if (!hasFactory && !hasDrawer) return true;
        boolean hasSettings = ReflectHelper.findClassIfExists("org.telegram.ui.SettingsActivity", cl) != null;
        return hasSettings && !hasFactory;
    }

    private static ClientObfuscationData load(ClientManager.Client type) {
        if (cache.containsKey(type)) return cache.get(type);

        ClientObfuscationData data = null;
        String clientPath = "assets/clients/" + type.name() + ".json";
        String clientJson = JsonAssetReader.readRaw(clientPath, false);

        String clientAliasPath = "assets/clients/Alias/" + type.name() + ".json";
        String clientAliasJson = JsonAssetReader.readRaw(clientAliasPath, false);

        if (clientJson != null && clientAliasJson != null)
            data = ClientObfuscationData.parse(clientJson, clientAliasJson);

        if (data != null)
            cache.put(type, data);
        return data;
    }

}