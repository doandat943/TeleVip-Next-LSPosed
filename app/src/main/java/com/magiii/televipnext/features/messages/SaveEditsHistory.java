package com.magiii.televipnext.features.messages;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.Database.MessageDatabase;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.calendar.ConverterCalendar;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.messages.MessageStorage;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.SQLite.SQLiteCursor;
import com.magiii.televipnext.virtuals.SQLite.SQLiteDatabase;
import com.magiii.televipnext.virtuals.SettingsIconResolver;
import com.magiii.televipnext.virtuals.messenger.DispatchQueue;
import com.magiii.televipnext.virtuals.messenger.MessageObject;
import com.magiii.televipnext.virtuals.messenger.MessagesStorage;
import com.magiii.televipnext.virtuals.messenger.UserConfig;
import com.magiii.televipnext.virtuals.tgnet.NativeByteBuffer;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;
import com.magiii.televipnext.virtuals.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class SaveEditsHistory {

    private static final int OPTION_EDIT_HISTORY = 8353847;

    private static final ConcurrentHashMap<String, String> pendingSelfEdits = new ConcurrentHashMap<>();
    private static MessageDatabase messageDatabase;
    private static volatile long lastMenuDialogId = 0L;
    private static volatile int lastMenuMsgId = 0;

    public static boolean isEnable = false;

    private static synchronized MessageDatabase getDatabase() {
        if (messageDatabase == null) {
            Context ctx = ApplicationLoaderHook.getApplicationContext();
            if (ctx == null) {
                ctx = Utils.getCurrentActivity();
            }
            if (ctx != null) {
                messageDatabase = new MessageDatabase(ctx);
            }
        }
        return messageDatabase;
    }

    public static void init() {
        try {
            if (isEnable) return;
            isEnable = true;

            getDatabase();

            Class<?> chatActivityClass = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
            if (chatActivityClass != null) {
                String fillMenuMethod = Obfuscate.getMethodName("ChatActivity", "fillMessageMenu");
                XposedCompat.hookAllMethods(chatActivityClass, fillMenuMethod, new BaseMethodHook() {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected void afterMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            Object rawMsgObj = extractSelectedMessageObject(param);
                            if (rawMsgObj == null) return;

                            MessageObject messageObject = new MessageObject(rawMsgObj);
                            TLRPC.Message message = messageObject.getMessageOwner();
                            if (message == null || message.get_Message() == null) return;

                            int msgId = message.getId();
                            if (msgId <= 0) return;

                            long dialogId = resolveDialogId(message.get_Message());
                            long legacyFromId = getLegacyFromId(message);
                            long dbKey = findExistingDbKey(dialogId, legacyFromId, msgId);
                            if (dbKey == 0L) return;

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
                            if (options == null || options.contains(OPTION_EDIT_HISTORY)) return;

                            lastMenuDialogId = dbKey;
                            lastMenuMsgId = msgId;

                            items.add(Translator.get(Keys.EditsHistory));
                            options.add(OPTION_EDIT_HISTORY);
                            if (icons != null && !ClientManager.is(ClientManager.Client.Nagram)) {
                                int iconId = resolveHistoryIconResId();
                                if (iconId == 0) {
                                    for (Integer existing : icons) {
                                        if (existing != null && existing >= 0x01000000) {
                                            iconId = existing;
                                            break;
                                        }
                                    }
                                }
                                icons.add(iconId);
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
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null || param.args.length != 1 || !(param.args[0] instanceof Integer)) {
                                return;
                            }
                            int option = (Integer) param.args[0];
                            if (option != OPTION_EDIT_HISTORY) return;

                            long dbKey = 0L;
                            int msgId = 0;
                            String currentText = null;
                            long currentDateMillis = 0L;

                            try {
                                ChatActivity chatActivity = new ChatActivity(param.thisObject);
                                MessageObject messageObject = chatActivity.getSelectedObject();
                                if (messageObject != null && messageObject.getMessageObject() != null) {
                                    TLRPC.Message message = messageObject.getMessageOwner();
                                    if (message != null && message.get_Message() != null && message.getId() > 0) {
                                        msgId = message.getId();
                                        currentText = getStringField(message.get_Message(), "message");
                                        int editSec = getIntField(message.get_Message(), "edit_date");
                                        if (editSec <= 0) editSec = getIntField(message.get_Message(), "date");
                                        if (editSec > 0) currentDateMillis = editSec * 1000L;
                                        dbKey = findExistingDbKey(resolveDialogId(message.get_Message()), getLegacyFromId(message), msgId);
                                    }
                                }
                            } catch (Throwable ignored) {}

                            if (dbKey == 0L) {
                                dbKey = lastMenuDialogId;
                                msgId = lastMenuMsgId;
                            }
                            if (dbKey == 0L || msgId <= 0) return;

                            MessageDatabase db = getDatabase();
                            if (db == null || !db.searchMessage(dbKey, msgId)) return;

                            showHistoryDialog(db, dbKey, msgId, currentText, currentDateMillis);
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });
            }

            Class<?> mcClass = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
            if (mcClass != null) {
                String processUpdatesMethod = Obfuscate.getMethodName("MessagesController", "processUpdateArray");
                XposedCompat.hookAllMethods(mcClass, processUpdatesMethod, new BaseMethodHook() {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected void beforeMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof ArrayList)) {
                                return;
                            }
                            ArrayList<Object> updates = (ArrayList<Object>) param.args[0];
                            if (updates == null || updates.isEmpty()) return;

                            for (Object update : updates) {
                                if (update == null) continue;
                                String clsName = update.getClass().getName();
                                if (clsName.contains("TL_updateEditMessage") || clsName.contains("TL_updateEditChannelMessage")) {
                                    Object rawNewMsg = getField(update, "message");
                                    if (rawNewMsg == null) continue;

                                    int msgId = getIntField(rawNewMsg, "id");
                                    long dialogId = resolveDialogId(rawNewMsg);
                                    String newText = getStringField(rawNewMsg, "message");
                                    if (msgId > 0 && dialogId != 0L) {
                                        capturePreviousMessageVersion(dialogId, msgId, newText, isChannelMessage(rawNewMsg));
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });
            }

            Class<?> storageClass = ClassLoad.getClass(ClassNames.MESSAGES_STORAGE);
            if (storageClass != null) {
                String putMessagesMethod = Obfuscate.getMethodName("MessagesStorage", "putMessages");
                XposedCompat.hookAllMethods(storageClass, putMessagesMethod, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null) return;

                            boolean hasEditLoadType = false;
                            Object messagesObj = null;
                            Class<?> tlMsgsClass = ClassLoad.getClass(ClassNames.TL_MESSAGES_MESSAGES, Utils.classLoader, false);
                            for (Object arg : param.args) {
                                if (arg instanceof Integer && ((Integer) arg) == -2) {
                                    hasEditLoadType = true;
                                } else if (arg != null && tlMsgsClass != null && tlMsgsClass.isInstance(arg)) {
                                    messagesObj = arg;
                                }
                            }
                            if (!hasEditLoadType || messagesObj == null) return;

                            ArrayList<Object> msgList = new TLRPC.messages_Messages(messagesObj).getMessages();
                            if (msgList == null || msgList.isEmpty()) return;

                            SQLiteDatabase sqLiteDatabase = new MessagesStorage(param.thisObject).getDatabase();
                            if (sqLiteDatabase == null) return;

                            for (Object rawMsg : msgList) {
                                if (rawMsg == null) continue;
                                int msgId = getIntField(rawMsg, "id");
                                long dialogId = resolveDialogId(rawMsg);
                                if (msgId <= 0 || dialogId == 0L) continue;

                                String newText = getStringField(rawMsg, "message");
                                String oldText = readPreviousMessageText(sqLiteDatabase, dialogId, msgId, isChannelMessage(rawMsg));
                                if (oldText != null && !oldText.isEmpty() && !oldText.equals(newText)) {
                                    MessageDatabase db = getDatabase();
                                    if (db != null) {
                                        db.addMessage(dialogId, msgId, oldText);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });
            }

            Class<?> smhClass = ClassLoad.getClass(ClassNames.SEND_MESSAGES_HELPER, Utils.classLoader, false);
            if (smhClass != null) {
                String sendParamsName = Obfuscate.getMethodName("SendMessagesHelper", "sendMessageParams");
                if (sendParamsName == null || "sendMessageParams".equals(sendParamsName)) {
                    sendParamsName = "sendMessage";
                }
                XposedCompat.hookAllMethods(smhClass, sendParamsName, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null || param.args.length != 1 || param.args[0] == null) {
                                return;
                            }
                            Class<?> paramsCls = ClassLoad.getClass(ClassNames.SEND_MESSAGE_PARAMS, Utils.classLoader, false);
                            if (paramsCls == null || !paramsCls.isInstance(param.args[0])) {
                                return;
                            }
                            Object params = param.args[0];
                            Object editingMsgObj = getField(params, Obfuscate.getFieldName("SendMessageParams", "editingMessageObject"));
                            if (editingMsgObj == null) return;

                            TLRPC.Message owner = new MessageObject(editingMsgObj).getMessageOwner();
                            if (owner == null || owner.get_Message() == null) return;

                            Object rawOwner = owner.get_Message();
                            int msgId = owner.getId();
                            long dialogId = resolveDialogId(rawOwner);
                            String oldText = getStringField(rawOwner, "message");
                            String newText = getStringField(params, Obfuscate.getFieldName("SendMessageParams", "message"));
                            if (newText == null) {
                                newText = getStringField(params, Obfuscate.getFieldName("SendMessageParams", "caption"));
                            }

                            if (msgId > 0 && dialogId != 0L && oldText != null && !oldText.isEmpty() && !oldText.equals(newText)) {
                                MessageDatabase db = getDatabase();
                                if (db != null) {
                                    db.addMessage(dialogId, msgId, oldText);
                                }
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });

                String editMsgName = Obfuscate.getMethodName("SendMessagesHelper", "editMessage");
                if (TextUtils.isEmpty(editMsgName)) {
                    editMsgName = "editMessage";
                }
                XposedCompat.hookAllMethods(smhClass, editMsgName, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null || param.args.length == 0 || param.args[0] == null) {
                                return;
                            }
                            Class<?> moCls = ClassLoad.getClass(ClassNames.MESSAGE_OBJECT, Utils.classLoader, false);
                            if (moCls != null && !moCls.isInstance(param.args[0])) {
                                return;
                            }
                            TLRPC.Message owner = new MessageObject(param.args[0]).getMessageOwner();
                            if (owner == null || owner.get_Message() == null) return;

                            Object rawOwner = owner.get_Message();
                            int msgId = owner.getId();
                            long dialogId = resolveDialogId(rawOwner);
                            if (msgId <= 0 || dialogId == 0L) return;

                            boolean isChannel = isChannelMessage(rawOwner);
                            String ownerTextBefore = getStringField(rawOwner, "message");

                            param.setObjectExtra("editDialogId", dialogId);
                            param.setObjectExtra("editMsgId", msgId);
                            param.setObjectExtra("editRawOwner", rawOwner);
                            param.setObjectExtra("editPreOwnerText", ownerTextBefore);

                            final MessagesStorage ms = MessagesStorage.getInstance(UserConfig.getSelectedAccount());
                            if (ms != null) {
                                postStorageTask(ms, () -> {
                                    try {
                                        SQLiteDatabase db = ms.getDatabase();
                                        String preDb = db != null ? readPreviousMessageText(db, dialogId, msgId, isChannel) : null;
                                        String best = !TextUtils.isEmpty(preDb) ? preDb : ownerTextBefore;
                                        if (!TextUtils.isEmpty(best)) {
                                            if (pendingSelfEdits.size() > 64) {
                                                pendingSelfEdits.clear();
                                            }
                                            pendingSelfEdits.put(dialogId + ":" + msgId, best);
                                        }
                                    } catch (Throwable t) {
                                        Logger.e(t);
                                    }
                                });
                            } else if (!TextUtils.isEmpty(ownerTextBefore)) {
                                if (pendingSelfEdits.size() > 64) {
                                    pendingSelfEdits.clear();
                                }
                                pendingSelfEdits.put(dialogId + ":" + msgId, ownerTextBefore);
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }

                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            Object didObj = param.getObjectExtra("editDialogId");
                            Object midObj = param.getObjectExtra("editMsgId");
                            Object rawOwner = param.getObjectExtra("editRawOwner");
                            if (!(didObj instanceof Long) || !(midObj instanceof Integer) || rawOwner == null) {
                                return;
                            }
                            long dialogId = (Long) didObj;
                            int msgId = (Integer) midObj;
                            String preOwnerText = param.getObjectExtra("editPreOwnerText") instanceof String
                                    ? (String) param.getObjectExtra("editPreOwnerText") : null;
                            String ownerTextAfter = getStringField(rawOwner, "message");
                            String key = dialogId + ":" + msgId;

                            MessagesStorage ms = MessagesStorage.getInstance(UserConfig.getSelectedAccount());
                            Runnable commitTask = () -> {
                                String captured = pendingSelfEdits.get(key);
                                String oldText = !TextUtils.isEmpty(captured) ? captured : preOwnerText;
                                if (!TextUtils.isEmpty(oldText) && ownerTextAfter != null && !oldText.equals(ownerTextAfter)) {
                                    pendingSelfEdits.remove(key);
                                    MessageDatabase db = getDatabase();
                                    if (db != null) {
                                        db.addMessage(dialogId, msgId, oldText);
                                    }
                                }
                            };
                            if (ms != null) {
                                postStorageTask(ms, commitTask);
                            } else {
                                commitTask.run();
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });
            }

            Class<?> cmClass = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
            if (cmClass != null) {
                XposedCompat.hookAllMethods(cmClass, "sendRequestInternal", new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        try {
                            if (ConfigManager.saveEditsHistory == null || !ConfigManager.saveEditsHistory.isEnable()) {
                                return;
                            }
                            if (param.args == null || param.args.length == 0 || param.args[0] == null) {
                                return;
                            }
                            Object req = param.args[0];
                            if (req.getClass().getName().contains("TL_messages_editMessage")) {
                                int msgId = getIntField(req, "id");
                                String newText = getStringField(req, "message");
                                Object peer = getField(req, "peer");
                                long dialogId = resolveInputPeerDialogId(peer);
                                boolean isChannel = peer != null && getLongField(peer, "channel_id") != 0L;
                                if (msgId > 0 && dialogId != 0L) {
                                    capturePreviousMessageVersion(dialogId, msgId, newText, isChannel, true);
                                }
                            }
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void capturePreviousMessageVersion(long dialogId, int msgId, String newText, boolean isChannel) {
        capturePreviousMessageVersion(dialogId, msgId, newText, isChannel, false);
    }

    private static void capturePreviousMessageVersion(long dialogId, int msgId, String newText, boolean isChannel, boolean checkPendingFirst) {
        final MessagesStorage messagesStorage;
        try {
            messagesStorage = MessagesStorage.getInstance(UserConfig.getSelectedAccount());
        } catch (Throwable t) {
            Logger.e(t);
            return;
        }
        if (messagesStorage == null) return;

        Runnable task = () -> {
            try {
                String oldText = checkPendingFirst ? pendingSelfEdits.remove(dialogId + ":" + msgId) : null;
                if (TextUtils.isEmpty(oldText)) {
                    SQLiteDatabase sqLiteDatabase = messagesStorage.getDatabase();
                    if (sqLiteDatabase == null) return;
                    oldText = readPreviousMessageText(sqLiteDatabase, dialogId, msgId, isChannel);
                }
                if (oldText != null && !oldText.isEmpty() && !oldText.equals(newText)) {
                    MessageDatabase db = getDatabase();
                    if (db != null) {
                        db.addMessage(dialogId, msgId, oldText);
                    }
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        };

        postStorageTask(messagesStorage, task);
    }

    private static void postStorageTask(MessagesStorage messagesStorage, Runnable task) {
        try {
            DispatchQueue queue = messagesStorage.getStorageQueue();
            if (queue != null) {
                queue.postRunnable(task);
                return;
            }
        } catch (Throwable ignored) {}

        MessageStorage.getStorage().post(task);
    }

    private static String readPreviousMessageText(SQLiteDatabase sqLiteDatabase, long dialogId, int msgId, boolean isChannel) {
        String text = queryMessageText(sqLiteDatabase,
                String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", msgId, dialogId));
        if (text != null) return text;

        text = queryMessageText(sqLiteDatabase,
                String.format(Locale.US, "SELECT data FROM messages_topics WHERE mid = %d AND uid = %d", msgId, dialogId));
        if (text != null) return text;

        if (!isChannel) {
            text = queryMessageText(sqLiteDatabase,
                    String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND is_channel = 0 LIMIT 1", msgId));
        }
        return text;
    }

    private static String queryMessageText(SQLiteDatabase sqLiteDatabase, String sql) {
        SQLiteCursor cursor = null;
        try {
            cursor = sqLiteDatabase.queryFinalized(sql, new Object[]{});
            if (cursor != null && cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null && data.nativeByteBuffer != null) {
                    try {
                        TLRPC.Message oldMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (oldMessage != null && oldMessage.get_Message() != null) {
                            return getStringField(oldMessage.get_Message(), "message");
                        }
                    } finally {
                        try {
                            data.reuse();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        } finally {
            if (cursor != null) {
                try {
                    cursor.dispose();
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static boolean isChannelMessage(Object rawMsg) {
        Object peerId = getField(rawMsg, "peer_id");
        return peerId != null && getLongField(peerId, "channel_id") != 0L;
    }

    private static void showHistoryDialog(MessageDatabase db, long dbKey, int msgId, String currentText, long currentDateMillis) {
        Activity activity = Utils.getCurrentActivity();
        if (activity == null) return;

        int maxMsgCount = db.getMaxMessageCount(dbKey, msgId);
        List<EditHistorySheet.VersionEntry> entries = new ArrayList<>();

        if (maxMsgCount > 0) {
            for (int i = 1; i <= maxMsgCount; i++) {
                String msg = db.getMessage(dbKey, msgId, i);
                if (msg == null) continue;
                long messageDate = db.getMessageDate(dbKey, msgId, i);
                entries.add(new EditHistorySheet.VersionEntry(i, msg, formatHistoryDate(messageDate), false));
            }
        }
        if (entries.isEmpty()) {
            String saved = db.getMessage(dbKey, msgId);
            if (saved != null) {
                long messageDate = db.getMessageDate(dbKey, msgId, 1);
                entries.add(new EditHistorySheet.VersionEntry(1, saved, formatHistoryDate(messageDate), false));
            }
        }

        if (!TextUtils.isEmpty(currentText)
                && (entries.isEmpty() || !currentText.equals(entries.get(entries.size() - 1).text))) {
            entries.add(new EditHistorySheet.VersionEntry(entries.size() + 1, currentText, formatHistoryDate(currentDateMillis), true));
        }

        if (entries.isEmpty()) return;

        String title = Translator.get(Keys.EditsHistory);
        if (TextUtils.isEmpty(title)) title = "Edits History";

        try {
            EditHistorySheet.show(activity, title, msgId, entries);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static String formatHistoryDate(long millis) {
        if (millis <= 0L) return "";
        String formatted = ConverterCalendar.formatDate(millis);
        if (!TextUtils.isEmpty(formatted)) {
            return formatted.trim();
        }
        String pattern = MessageTimeModifier.isSecondsEnabled()
                ? "yyyy-MM-dd HH:mm:ss"
                : "yyyy-MM-dd HH:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
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

    private static long findExistingDbKey(long dialogId, long legacyFromId, int msgId) {
        MessageDatabase db = getDatabase();
        if (db == null || msgId <= 0) return 0L;
        if (dialogId != 0L && db.searchMessage(dialogId, msgId)) return dialogId;
        if (legacyFromId != 0L && legacyFromId != dialogId && db.searchMessage(legacyFromId, msgId)) {
            return legacyFromId;
        }
        return 0L;
    }

    private static long resolveDialogId(Object rawMsg) {
        if (rawMsg == null) return 0L;
        try {
            long did = MessageObject.getDialogId(new TLRPC.Message(rawMsg));
            if (did != 0L) return did;
        } catch (Throwable ignored) {}

        long did = getLongField(rawMsg, Obfuscate.getFieldName("Message", "dialog_id"));
        if (did != 0L) return did;

        Object peerId = getField(rawMsg, "peer_id");
        if (peerId != null) {
            long cid = getLongField(peerId, "channel_id");
            if (cid != 0L) return -cid;
            long gid = getLongField(peerId, "chat_id");
            if (gid != 0L) return -gid;
            long uid = getLongField(peerId, "user_id");
            if (uid != 0L) return uid;
        }
        return 0L;
    }

    private static long getLegacyFromId(TLRPC.Message message) {
        try {
            TLRPC.Peer from = message.getFrom_id();
            if (from != null) {
                return getDialogId(from);
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private static Object firstNonNull(ArrayList<?> list) {
        for (Object item : list) {
            if (item != null) return item;
        }
        return null;
    }

    private static boolean hasResourceId(ArrayList<Integer> list) {
        for (Integer val : list) {
            if (val != null && val >= 0x01000000) {
                return true;
            }
        }
        return false;
    }

    private static int resolveHistoryIconResId() {
        Context ctx = ApplicationLoaderHook.getApplicationContext();
        if (ctx != null) {
            String pkg = ctx.getPackageName();
            String[] names = {"msg_log", "msg_info", "msg_edit", "msg_settings"};
            for (String name : names) {
                try {
                    int id = ctx.getResources().getIdentifier(name, "drawable", pkg);
                    if (id != 0) return id;
                } catch (Throwable ignored) {}
            }
        }
        return SettingsIconResolver.getIconSettings();
    }

    private static Object getField(Object obj, String field) {
        if (obj == null || field == null) return null;
        try {
            return ReflectHelper.getObjectField(obj, field);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String getStringField(Object obj, String field) {
        Object val = getField(obj, field);
        return val instanceof String ? (String) val : null;
    }

    private static int getIntField(Object obj, String field) {
        if (obj == null || field == null) return 0;
        try {
            return ReflectHelper.getIntField(obj, field);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long getLongField(Object obj, String field) {
        if (obj == null || field == null) return 0L;
        try {
            return ReflectHelper.getLongField(obj, field);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long resolveInputPeerDialogId(Object inputPeer) {
        if (inputPeer == null) return 0L;
        long cid = getLongField(inputPeer, "channel_id");
        if (cid != 0L) return -cid;
        long gid = getLongField(inputPeer, "chat_id");
        if (gid != 0L) return -gid;
        long uid = getLongField(inputPeer, "user_id");
        if (uid != 0L) return uid;
        if (inputPeer.getClass().getSimpleName().contains("Self")) {
            try {
                UserConfig uc = UserConfig.getInstance(UserConfig.getSelectedAccount());
                if (uc != null && uc.get_UserConfig() != null) {
                    String mName = Obfuscate.getMethodName("UserConfig", "getClientUserId");
                    if (mName != null && !"getClientUserId".equals(mName)) {
                        return (long) ReflectHelper.callMethod(uc.get_UserConfig(), mName);
                    }
                    return uc.getClientUserId();
                }
            } catch (Throwable ignored) {}
        }
        return 0L;
    }

    private static long getDialogId(TLRPC.Peer peer) {
        long user_id = peer.getUser_id();
        long chat_id = peer.getChat_id();
        long channel_id = peer.getChannel_id();
        long dialogId = 0;

        if (user_id != 0) {
            dialogId = user_id;
        } else if (chat_id != 0) {
            dialogId = chat_id;
        } else if (channel_id != 0) {
            dialogId = channel_id;
        }
        return dialogId;
    }

}
