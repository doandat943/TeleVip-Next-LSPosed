package com.magiii.televipnext.features.ui;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Method;

/**
 * Blocks Telegram Sponsored Ads (channels, bots, video player, and global search peers)
 * via both MessagesController#getSponsoredMessages and ConnectionsManager#sendRequestInternal.
 */
public class HideSponsoredAds {

    private static boolean loaded;
    private static Class<?> tlGetSponsoredMessagesCls;
    private static Class<?> tlGetSponsoredPeersCls;
    private static boolean tlClassesResolved;

    public static boolean isEnabled() {
        return ConfigManager.hideSponsoredAds != null && ConfigManager.hideSponsoredAds.isEnable();
    }

    /**
     * Installs both controller and network-request hooks once for instant runtime toggling.
     */
    public static void init() {
        if (loaded) return;
        loaded = true;
        try {
            hookMessagesController();
            hookConnectionsManager();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void hookMessagesController() {
        Class<?> mcCls = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER, Utils.classLoader, false);
        if (mcCls == null) return;

        String mapped = Obfuscate.getMethodName("MessagesController", "getSponsoredMessages");
        Method target = ReflectHelper.findMethodExactIfExists(mcCls, mapped, long.class);
        if (target == null && !"getSponsoredMessages".equals(mapped)) {
            target = ReflectHelper.findMethodExactIfExists(mcCls, "getSponsoredMessages", long.class);
        }
        if (target != null) {
            HMethod.hookMethod(target, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (isEnabled()) {
                        param.setResult(null);
                    }
                }
            });
        }
    }

    private static void hookConnectionsManager() {
        Class<?> cmCls = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER, Utils.classLoader, false);
        if (cmCls == null) return;

        Class<?>[] expectedTypes = new Class[]{
                ClassLoad.getClass(ClassNames.TL_OBJECT),
                ClassLoad.getClass(ClassNames.REQUEST_DELEGATE),
                ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP),
                ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE),
                ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE),
                int.class, int.class, int.class, boolean.class, int.class
        };
        HMethod.hookMethod(
                cmCls,
                Obfuscate.getMethodName("ConnectionsManager", "sendRequestInternal"),
                ArgsResolver.merge("sendRequestInternal", expectedTypes, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (!isEnabled() || param.args == null || param.args.length == 0) return;
                        Object req = param.args[0];
                        if (req != null && isSponsoredRequest(req)) {
                            param.setResult(null);
                        }
                    }
                })
        );
    }

    private static boolean isSponsoredRequest(Object req) {
        if (!tlClassesResolved) {
            tlClassesResolved = true;
            tlGetSponsoredMessagesCls = ClassLoad.getClass(
                    ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES, Utils.classLoader, false);
            tlGetSponsoredPeersCls = ClassLoad.getClass(
                    ClassNames.TL_CONTACTS_GET_SPONSORED_PEERS, Utils.classLoader, false);
        }
        Class<?> cls = req.getClass();
        if (cls.equals(tlGetSponsoredMessagesCls) || cls.equals(tlGetSponsoredPeersCls)) {
            return true;
        }
        if (!ClientManager.isTgnetObfuscated()) {
            String name = cls.getName();
            return name.contains("TL_messages_getSponsoredMessages")
                    || name.contains("TL_channels_getSponsoredMessages")
                    || name.contains("TL_contacts_getSponsoredPeers");
        }
        return false;
    }
}
