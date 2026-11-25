package com.my.televip.obfuscate;

import com.my.televip.Clients.ClientManager;
import com.my.televip.obfuscate.auto.AutoMappingResolver;
import com.my.televip.obfuscate.struct.ClientObfuscationData;
import com.my.televip.utils.JsonAssetReader;
import com.my.televip.utils.Utils;

import java.util.EnumMap;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

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
                currentClientData = load(type);
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
            // Only scan DEX if the client is actually obfuscated (MessagesController not found by raw name)
            if (XposedHelpers.findClassIfExists("org.telegram.messenger.MessagesController", Utils.classLoader) == null) {
                currentClientData = AutoMappingResolver.resolveOrLoadCache(
                        Utils.getCurrentActivity(),
                        Utils.sourceDir,
                        Utils.classLoader
                );
            }
            return currentClientData;
        }
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