package com.magiii.televipnext.features.messages;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.widget.Toast;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.messages.MessageStorage;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.SettingsIconResolver;
import com.magiii.televipnext.virtuals.androidx.LongSparseArray;
import com.magiii.televipnext.virtuals.messenger.MessageObject;
import com.magiii.televipnext.virtuals.messenger.MessagesController;
import com.magiii.televipnext.virtuals.messenger.MessagesStorage;
import com.magiii.televipnext.virtuals.messenger.NotificationCenter;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;
import com.magiii.televipnext.virtuals.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class ShowDeletedMessages {

    public static final int FLAG_DELETED = 1 << 31;
    private static final int OPTION_LOCAL_DELETE = 99989;
    private static final int RED_DELETE_COLOR = 0xFFFF453A;

    private static volatile long allowDeleteUntilMs = 0L;
    private static final ConcurrentHashMap<Integer, Long> allowedLocalDeleteIds = new ConcurrentHashMap<>();
    private static boolean hooksInstalled = false;
    private static boolean menuHooksInstalled = false;

    private static long lastMenuDialogId = 0L;
    private static long lastMenuChannelId = 0L;
    private static int lastMenuMsgId = 0;

    public static boolean isEnable = false;

    public static void allowLocalDeleteFor(long durationMs) {
        long target = System.currentTimeMillis() + Math.max(0L, durationMs);
        if (target > allowDeleteUntilMs) {
            allowDeleteUntilMs = target;
        }
    }

    public static void allowLocalDeleteIds(Collection<?> ids, long durationMs) {
        if (ids == null || ids.isEmpty()) return;
        long now = System.currentTimeMillis();
        long expiry = now + Math.max(0L, durationMs);
        if (allowedLocalDeleteIds.size() > 256) {
            Iterator<Map.Entry<Integer, Long>> it = allowedLocalDeleteIds.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() < now) {
                    it.remove();
                }
            }
        }
        for (Object item : ids) {
            if (item instanceof Integer) {
                int id = (Integer) item;
                if (id > 0) {
                    allowedLocalDeleteIds.merge(id, expiry, Math::max);
                }
            }
        }
    }

    private static boolean isDeleteAllowedNow() {
        return System.currentTimeMillis() <= allowDeleteUntilMs;
    }

    private static boolean areAllIdsAllowed(List<?> ids) {
        if (ids == null || ids.isEmpty()) return false;
        long now = System.currentTimeMillis();
        boolean sawInt = false;
        for (Object item : ids) {
            if (!(item instanceof Integer)) continue;
            sawInt = true;
            Long exp = allowedLocalDeleteIds.get(item);
            if (exp == null || exp < now) return false;
        }
        return sawInt;
    }

    public static boolean isAnyScopeEnabled() {
        return ConfigManager.isShowDeletedAnyEnabled();
    }

    public static void markMessagesDeletedForController(MessagesStorage messagesStorage, long dialogId, ArrayList<Integer> delMsg) {
        MessageStorage.markMessagesDeleted(messagesStorage, dialogId, delMsg);
    }

    private static boolean shouldKeepDialogUpdate(MessagesController mc, long dialogId) {
        if (dialogId > 0L) {
            return ConfigManager.showDeletedPrivate != null && ConfigManager.showDeletedPrivate.isEnable();
        }
        if (dialogId < 0L) {
            return shouldKeepChannelUpdate(mc, -dialogId);
        }
        return (ConfigManager.showDeletedPrivate != null && ConfigManager.showDeletedPrivate.isEnable())
                || (ConfigManager.showDeletedGroups != null && ConfigManager.showDeletedGroups.isEnable());
    }

    private static boolean shouldKeepChannelUpdate(MessagesController mc, long channelId) {
        boolean groupsOn = ConfigManager.showDeletedGroups != null && ConfigManager.showDeletedGroups.isEnable();
        boolean channelsOn = ConfigManager.showDeletedChannels != null && ConfigManager.showDeletedChannels.isEnable();
        if (groupsOn == channelsOn) return channelsOn;

        try {
            Object chat = mc != null ? mc.getChat(channelId) : null;
            if (chat != null) {
                boolean broadcast = getBooleanField(chat, "broadcast");
                boolean megagroup = getBooleanField(chat, "megagroup");
                return (broadcast && !megagroup) ? channelsOn : groupsOn;
            }
        } catch (Throwable ignored) {}
        return channelsOn || groupsOn;
    }

    private static Boolean classifyNonChannelMessage(MessagesController mc, int msgId) {
        try {
            SparseArray<Object> dialogMessages = mc != null ? mc.getDialogMessagesByIds() : null;
            Object msgObj = dialogMessages != null ? dialogMessages.get(msgId) : null;
            if (msgObj == null) return null;
            MessageObject mo = new MessageObject(msgObj);
            TLRPC.Message owner = mo.getMessageOwner();
            if (owner == null || owner.get_Message() == null) return null;
            long did = resolveDialogId(mo, owner.get_Message());
            if (did > 0L) return Boolean.TRUE;
            if (did < 0L) return Boolean.FALSE;
            Object peer = ReflectHelper.getObjectField(owner.get_Message(), "peer_id");
            if (peer != null) {
                long chatId = getLongField(peer, "chat_id");
                if (chatId != 0L) return Boolean.FALSE; // basic group
                long userId = getLongField(peer, "user_id");
                if (userId != 0L) return Boolean.TRUE;  // private chat
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean processDeletedMessage(MessagesController messagesController, Object item, boolean updateDeleteChannelMessages, boolean updateDeleteMessages) {
        if (updateDeleteChannelMessages) {
            TLRPC.TL_updateDeleteChannelMessages channelMessages = new TLRPC.TL_updateDeleteChannelMessages(item);
            long channelId = channelMessages.getChannelID();
            if (!shouldKeepChannelUpdate(messagesController, channelId)) {
                allowLocalDeleteIds(channelMessages.getMessages(), 3000L);
                return false;
            }

            LongSparseArray dialogMessage = messagesController.getDialogMessage();
            ArrayList<Object> dialogMessages = dialogMessage != null ? dialogMessage.get(-channelId) : null;
            if (dialogMessages != null) {
                for (final Object msgObj : dialogMessages) {
                    TLRPC.Message owner = new MessageObject(msgObj).getMessageOwner();
                    if (owner != null && channelMessages.getMessages() != null
                            && channelMessages.getMessages().contains(owner.getId())) {
                        owner.setFlags(owner.getFlags() | FLAG_DELETED);
                    }
                }
            }

            markMessagesDeletedForController(messagesController.getMessagesStorage(), -channelId, channelMessages.getMessages());
            return true;
        }

        if (updateDeleteMessages) {
            boolean privOn = ConfigManager.showDeletedPrivate != null && ConfigManager.showDeletedPrivate.isEnable();
            boolean grpOn = ConfigManager.showDeletedGroups != null && ConfigManager.showDeletedGroups.isEnable();
            ArrayList<Integer> messages = new TLRPC.TL_updateDeleteMessages(item).getMessages();
            if (!privOn && !grpOn) {
                allowLocalDeleteIds(messages, 3000L);
                return false;
            }

            if (messages == null || messages.isEmpty()) return true;

            SparseArray<Object> dialogMessages = messagesController.getDialogMessagesByIds();
            ArrayList<Integer> keepIds = new ArrayList<>();
            ArrayList<Integer> passIds = new ArrayList<>();

            for (int id : messages) {
                Boolean isPrivate = classifyNonChannelMessage(messagesController, id);
                boolean keep = isPrivate != null ? (isPrivate ? privOn : grpOn) : true;
                if (keep) {
                    keepIds.add(id);
                    Object msgObj = dialogMessages != null ? dialogMessages.get(id) : null;
                    if (msgObj != null) {
                        TLRPC.Message owner = new MessageObject(msgObj).getMessageOwner();
                        if (owner != null) {
                            owner.setFlags(owner.getFlags() | FLAG_DELETED);
                        }
                    }
                } else {
                    passIds.add(id);
                }
            }

            if (!keepIds.isEmpty()) {
                MessageStorage.markMessagesDeleted(messagesController.getMessagesStorage(), 0, keepIds, privOn, grpOn);
            }
            if (passIds.isEmpty()) {
                return true;
            }
            allowLocalDeleteIds(passIds, 3000L);
            messages.clear();
            messages.addAll(passIds);
            return false;
        }
        return false;
    }

    public static void initProcessUpdateArray() {
        try {
            HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),
                    Obfuscate.getMethodName("MessagesController", "processUpdateArray"),
                    ArgsResolver.merge("processUpdateArray", new Class[]{ArrayList.class, ArrayList.class, ArrayList.class, boolean.class, int.class},
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (!isAnyScopeEnabled()) return;
                                    try {
                                        if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof ArrayList)) {
                                            return;
                                        }
                                        @SuppressWarnings("unchecked")
                                        ArrayList<Object> updates = (ArrayList<Object>) param.args[0];
                                        if (updates == null || updates.isEmpty()) {
                                            return;
                                        }
                                        MessagesController messagesController = new MessagesController(param.thisObject);
                                        ArrayList<Object> result = new ArrayList<>();

                                        for (Object update : updates) {
                                            if (update == null) {
                                                result.add(null);
                                                continue;
                                            }
                                            boolean updateDeleteChannelMessages = ClassLoad.isTlInstance(
                                                    update, ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES, "TL_updateDeleteChannelMessages");
                                            boolean updateDeleteMessages = ClassLoad.isTlInstance(
                                                    update, ClassNames.TL_UPDATE_DELETE_MESSAGES, "TL_updateDeleteMessages");

                                            if (updateDeleteChannelMessages || updateDeleteMessages) {
                                                if (processDeletedMessage(messagesController, update,
                                                        updateDeleteChannelMessages, updateDeleteMessages)) {
                                                    continue;
                                                }
                                            }

                                            result.add(update);
                                        }

                                        param.args[0] = result;
                                    } catch (Throwable e) {
                                        Logger.e(e);
                                    }
                                }
                            }));
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static void init() {
        isEnable = true;
        initMenuHooks();
        if (!MessageTimeModifier.loaded) {
            MessageTimeModifier.init();
        }
        if (hooksInstalled) return;
        hooksInstalled = true;

        try {
            Class<?> msClass = ClassLoad.getClass(ClassNames.MESSAGES_STORAGE);
            if (msClass != null) {
                XposedCompat.hookAllMethods(
                        msClass,
                        Obfuscate.getMethodName("MessagesStorage", "markMessagesAsDeleted"),
                        new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (!isAnyScopeEnabled()) return;
                                if (param.args == null || param.args.length < 5
                                        || !(param.args[0] instanceof Long)
                                        || !(param.args[1] instanceof ArrayList)) {
                                    return;
                                }
                                ArrayList<?> ids = (ArrayList<?>) param.args[1];
                                if (isDeleteAllowedNow() || areAllIdsAllowed(ids)) return;
                                long dialogId = (Long) param.args[0];
                                if (!shouldKeepDialogUpdate(MessagesController.getMessagesController(), dialogId)) {
                                    return;
                                }
                                param.setResult(null);
                            }
                        }
                );
            }

            HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.NOTIFICATIONS_CONTROLLER),
                    Obfuscate.getMethodName("NotificationsController", "removeDeletedMessagesFromNotifications"),
                    ArgsResolver.merge("removeDeletedMessagesFromNotifications", new Class[]{ClassLoad.getClass(ClassNames.LONG_SPARES_ARRAY), boolean.class},
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (isAnyScopeEnabled() && !isDeleteAllowedNow()) {
                                        param.setResult(null);
                                    }
                                }
                            }));

            Class<?> mcClass = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
            if (mcClass != null) {
                XposedCompat.hookAllMethods(
                        mcClass,
                        Obfuscate.getMethodName("MessagesController", "deleteMessages"),
                        new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (param.args == null || param.args.length < 6 || !(param.args[0] instanceof ArrayList)) {
                                    return;
                                }
                                ArrayList<?> ids = (ArrayList<?>) param.args[0];
                                if (!ids.isEmpty()) {
                                    allowLocalDeleteIds(ids, 5000L);
                                } else {
                                    allowLocalDeleteFor(1500L);
                                }
                            }
                        }
                );
            }

            HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER),
                    Obfuscate.getMethodName("NotificationCenter", "postNotificationName"),
                    ArgsResolver.merge("postNotificationName", new Class[]{int.class, Object[].class},
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (!isAnyScopeEnabled()) return;
                                    int id = (int) param.args[0];
                                    if (id == NotificationCenter.getMessagesDeleted()) {
                                        if (isDeleteAllowedNow()) return;
                                        Object[] notifArgs = (param.args.length > 1 && param.args[1] instanceof Object[])
                                                ? (Object[]) param.args[1] : null;
                                        if (notifArgs != null && notifArgs.length >= 1
                                                && notifArgs[0] instanceof ArrayList
                                                && areAllIdsAllowed((ArrayList<?>) notifArgs[0])) {
                                            return;
                                        }
                                        if (notifArgs != null && notifArgs.length >= 2 && notifArgs[1] instanceof Long) {
                                            long channelId = (Long) notifArgs[1];
                                            if (channelId != 0L && !shouldKeepChannelUpdate(
                                                    MessagesController.getMessagesController(), Math.abs(channelId))) {
                                                return;
                                            }
                                        }
                                        param.setResult(null);
                                    }
                                }
                            }));

            ShowDeletedMessages.initProcessUpdateArray();
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    /**
     * Installs ChatActivity menu hooks so any locally retained FLAG_DELETED message can be removed locally.
     */
    public static void initMenuHooks() {
        if (!MessageTimeModifier.loaded) {
            MessageTimeModifier.init();
        }
        if (menuHooksInstalled) return;
        menuHooksInstalled = true;

        try {
            Class<?> chatActivityClass = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
            if (chatActivityClass == null) return;

            String fillMenuMethod = Obfuscate.getMethodName("ChatActivity", "fillMessageMenu");
            XposedCompat.hookAllMethods(chatActivityClass, fillMenuMethod, new BaseMethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void afterMethod(MethodHookParam param) {
                    try {
                        Object rawMsgObj = extractSelectedMessageObject(param);
                        if (rawMsgObj == null) return;

                        MessageObject messageObject = new MessageObject(rawMsgObj);
                        TLRPC.Message message = messageObject.getMessageOwner();
                        if (message == null || message.get_Message() == null) return;
                        if ((message.getFlags() & FLAG_DELETED) == 0) return;

                        int msgId = message.getId();
                        if (msgId <= 0) return;

                        List<ArrayList<?>> allLists = new ArrayList<>();
                        if (param.args != null) {
                            for (Object arg : param.args) {
                                if (arg instanceof ArrayList) {
                                    allLists.add((ArrayList<?>) arg);
                                }
                            }
                        }
                        if (allLists.size() < 2) return;

                        ArrayList<CharSequence> items = null;
                        ArrayList<Integer> icons = null;
                        ArrayList<Integer> options = null;
                        List<ArrayList<Integer>> intLists = new ArrayList<>();

                        for (ArrayList<?> list : allLists) {
                            Object first = firstNonNull(list);
                            if (first instanceof CharSequence) {
                                if (items == null) items = (ArrayList<CharSequence>) list;
                            } else if (first instanceof Integer) {
                                intLists.add((ArrayList<Integer>) list);
                            }
                        }
                        if (items == null) return;

                        for (ArrayList<Integer> intList : intLists) {
                            if (hasResourceId(intList)) {
                                icons = intList;
                            } else if (options == null) {
                                options = intList;
                            }
                        }
                        if (options == null) {
                            for (ArrayList<Integer> intList : intLists) {
                                if (intList != icons) {
                                    options = intList;
                                    break;
                                }
                            }
                        }
                        if (options == null || options.contains(OPTION_LOCAL_DELETE)) return;

                        lastMenuDialogId = resolveDialogId(messageObject, message.get_Message());
                        lastMenuChannelId = resolveChannelId(message.get_Message());
                        lastMenuMsgId = msgId;

                        String label = Translator.get(Keys.DeleteLocally);
                        SpannableString redLabel = new SpannableString(label);
                        redLabel.setSpan(new ForegroundColorSpan(RED_DELETE_COLOR), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        items.add(redLabel);
                        options.add(OPTION_LOCAL_DELETE);
                        if (icons != null && !ClientManager.is(ClientManager.Client.Nagram)) {
                            icons.add(resolveDeleteIconResId(icons));
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });

            String processOptionMethod = Obfuscate.getMethodName("ChatActivity", "processSelectedOption");
            XposedCompat.hookAllMethods(chatActivityClass, processOptionMethod, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) {
                            return;
                        }
                        if ((Integer) param.args[0] != OPTION_LOCAL_DELETE) return;

                        long dialogId = 0L;
                        long channelId = 0L;
                        int msgId = 0;

                        try {
                            MessageObject selected = new ChatActivity(param.thisObject).getSelectedObject();
                            if (selected != null && selected.getMessageObject() != null) {
                                TLRPC.Message message = selected.getMessageOwner();
                                if (message != null && message.get_Message() != null && message.getId() > 0) {
                                    msgId = message.getId();
                                    dialogId = resolveDialogId(selected, message.get_Message());
                                    channelId = resolveChannelId(message.get_Message());
                                }
                            }
                        } catch (Throwable ignored) {}

                        if (msgId <= 0) {
                            dialogId = lastMenuDialogId;
                            channelId = lastMenuChannelId;
                            msgId = lastMenuMsgId;
                        }
                        if (msgId <= 0) return;

                        MessageStorage.deleteLocalMessage(
                                MessagesStorage.getMessagesStorage(), dialogId, channelId, msgId, null);
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    public static void clearAllDeletedMessages(final Context context) {
        try {
            MessageStorage.clearAllDeletedMessages(MessagesStorage.getMessagesStorage(), count -> {
                Context toastCtx = context != null ? context : ApplicationLoaderHook.getApplicationContext();
                if (toastCtx == null) return;
                String msg = count > 0
                        ? String.format(Translator.get(Keys.ClearedDeletedCount), count)
                        : Translator.get(Keys.NoDeletedMessages);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(toastCtx, msg, Toast.LENGTH_SHORT).show());
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static Object extractSelectedMessageObject(BaseMethodHook.MethodHookParam param) {
        if (param.args != null && param.args.length > 0 && param.args[0] != null) {
            Class<?> moClass = ClassLoad.getClass(ClassNames.MESSAGE_OBJECT, Utils.classLoader, false);
            if (moClass == null || moClass.isInstance(param.args[0])) {
                return param.args[0];
            }
        }
        try {
            MessageObject selected = new ChatActivity(param.thisObject).getSelectedObject();
            return selected != null ? selected.getMessageObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long resolveDialogId(MessageObject messageObject, Object rawMsg) {
        try {
            long did = messageObject.getDialogId();
            if (did != 0L) return did;
        } catch (Throwable ignored) {}
        try {
            Object peerId = ReflectHelper.getObjectField(rawMsg, "peer_id");
            if (peerId != null) {
                long cid = getLongField(peerId, "channel_id");
                if (cid != 0L) return -cid;
                long gid = getLongField(peerId, "chat_id");
                if (gid != 0L) return -gid;
                long uid = getLongField(peerId, "user_id");
                if (uid != 0L) return uid;
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private static long resolveChannelId(Object rawMsg) {
        try {
            Object peerId = ReflectHelper.getObjectField(rawMsg, "peer_id");
            if (peerId != null) {
                long cid = getLongField(peerId, "channel_id");
                if (cid > 0L) return cid;
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private static int resolveDeleteIconResId(ArrayList<Integer> existingIcons) {
        int id = SettingsIconResolver.resolveDrawableId("msg_delete", "msg_clear");
        if (id != 0) return id;
        if (existingIcons != null) {
            for (Integer iconId : existingIcons) {
                if (iconId != null && iconId >= 0x01000000) return iconId;
            }
        }
        return SettingsIconResolver.getIconSettings();
    }

    private static Object firstNonNull(ArrayList<?> list) {
        for (Object item : list) {
            if (item != null) return item;
        }
        return null;
    }

    private static boolean hasResourceId(ArrayList<Integer> list) {
        for (Integer val : list) {
            if (val != null && val >= 0x01000000) return true;
        }
        return false;
    }

    private static boolean getBooleanField(Object obj, String field) {
        try {
            return ReflectHelper.getBooleanField(obj, field);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long getLongField(Object obj, String field) {
        try {
            return ReflectHelper.getLongField(obj, field);
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}