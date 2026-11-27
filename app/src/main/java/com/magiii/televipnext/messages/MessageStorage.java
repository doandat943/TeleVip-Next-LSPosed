package com.magiii.televipnext.messages;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.util.SparseArray;

import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.features.messages.ShowDeletedMessages;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.virtuals.SQLite.SQLiteCursor;
import com.magiii.televipnext.virtuals.SQLite.SQLiteDatabase;
import com.magiii.televipnext.virtuals.SQLite.SQLitePreparedStatement;
import com.magiii.televipnext.virtuals.androidx.LongSparseArray;
import com.magiii.televipnext.virtuals.messenger.DispatchQueue;
import com.magiii.televipnext.virtuals.messenger.MessageObject;
import com.magiii.televipnext.virtuals.messenger.MessagesController;
import com.magiii.televipnext.virtuals.messenger.MessagesStorage;
import com.magiii.televipnext.virtuals.messenger.NotificationCenter;
import com.magiii.televipnext.virtuals.messenger.UserConfig;
import com.magiii.televipnext.virtuals.tgnet.NativeByteBuffer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageStorage {

    public interface ClearCallback {
        void onComplete(int count);
    }

    private static final Handler storage = new Handler(makeLooper("Storage"));
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static File getStorageFile() {
        File dir = new File(
                ApplicationLoaderHook.getApplicationContext()
                        .getFilesDir().getParentFile(),
                "TeleVip"
        );
        if (!dir.exists() && !dir.mkdir()) {
            Logger.w("Cannot create " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static Looper makeLooper(String str) {
        HandlerThread handlerThread = new HandlerThread("TeleVip - " + str, Process.THREAD_PRIORITY_DISPLAY);
        handlerThread.start();
        return handlerThread.getLooper();
    }

    public static void markMessagesDeleted(MessagesStorage messagesStorage, final long dialogId, ArrayList<Integer> delMsg) {
        markMessagesDeleted(messagesStorage, dialogId, delMsg, true, true);
    }

    public static void markMessagesDeleted(MessagesStorage messagesStorage, final long dialogId, ArrayList<Integer> delMsg, boolean allowPrivate, boolean allowGroups) {
        if (messagesStorage == null || delMsg == null || delMsg.isEmpty()) return;
        if (dialogId == 0 && !allowPrivate && !allowGroups) return;

        postToStorage(messagesStorage, () -> {
            try {
                SQLiteDatabase db = messagesStorage.getDatabase();
                if (db == null) return;
                String joinedIds = TextUtils.join(",", delMsg);
                String uidFilter = "";
                if (dialogId == 0 && allowPrivate != allowGroups) {
                    uidFilter = allowPrivate ? " AND uid > 0" : " AND uid < 0";
                }
                for (int i = 0; i < 2; ++i) {
                    final String table = i == 0 ? "messages_v2" : "messages_topics";
                    String query = "SELECT data,mid,uid FROM " + table + " WHERE "
                            + (dialogId == 0 ? "is_channel" : "uid") + " = " + dialogId
                            + uidFilter
                            + " AND mid IN (" + joinedIds + ");";
                    String update = "UPDATE " + table + " SET data = ? WHERE uid = ? AND mid = ?";

                    SQLiteCursor cursor = null;
                    SQLitePreparedStatement state = null;
                    try {
                        cursor = db.queryFinalized(query, new Object[]{});
                        state = db.executeFast(update);
                        if (cursor == null || state == null) continue;

                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data == null || data.nativeByteBuffer == null) continue;
                            try {
                                int mid = cursor.intValue(1);
                                long lastDialogId = cursor.longValue(2);

                                data.position(4);
                                int flags = data.readInt32(true);
                                flags |= ShowDeletedMessages.FLAG_DELETED;
                                data.position(4);
                                data.writeInt32(flags);
                                data.position(0);

                                state.requery();
                                state.bindByteBuffer(1, data);
                                state.bindLong(2, lastDialogId);
                                state.bindInteger(3, mid);
                                state.step();
                            } finally {
                                data.reuse();
                            }
                        }
                    } finally {
                        if (cursor != null) {
                            try { cursor.dispose(); } catch (Throwable ignored) {}
                        }
                        if (state != null) {
                            try { state.dispose(); } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable e) {
                Logger.e(e);
            }
        });
    }

    /**
     * Deletes a single deleted message from Telegram's local SQLite cache and notifies open chats.
     */
    public static void deleteLocalMessage(MessagesStorage messagesStorage, final long dialogId, final long channelId, final int msgId, final Runnable onComplete) {
        if (messagesStorage == null || msgId <= 0) {
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }

        postToStorage(messagesStorage, () -> {
            try {
                SQLiteDatabase db = messagesStorage.getDatabase();
                if (db != null) {
                    for (int i = 0; i < 2; ++i) {
                        final String table = i == 0 ? "messages_v2" : "messages_topics";
                        String sql = dialogId != 0
                                ? ("DELETE FROM " + table + " WHERE uid = ? AND mid = ?")
                                : ("DELETE FROM " + table + " WHERE mid = ?");
                        SQLitePreparedStatement state = null;
                        try {
                            state = db.executeFast(sql);
                            if (state != null) {
                                state.requery();
                                if (dialogId != 0) {
                                    state.bindLong(1, dialogId);
                                    state.bindInteger(2, msgId);
                                } else {
                                    state.bindInteger(1, msgId);
                                }
                                state.step();
                            }
                        } catch (Throwable ignored) {
                        } finally {
                            if (state != null) {
                                try { state.dispose(); } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Logger.e(e);
            } finally {
                ArrayList<Integer> ids = new ArrayList<>(1);
                ids.add(msgId);
                mainHandler.post(() -> {
                    pruneInMemoryMessages(dialogId, channelId, ids);
                    if (onComplete != null) onComplete.run();
                });
                NotificationCenter.postMessagesDeleted(UserConfig.getSelectedAccount(), ids, channelId);
            }
        });
    }

    /**
     * Clears every locally preserved deleted message (with FLAG_DELETED) across SQLite tables and active UI.
     */
    public static void clearAllDeletedMessages(MessagesStorage messagesStorage, final ClearCallback callback) {
        if (messagesStorage == null) {
            if (callback != null) mainHandler.post(() -> callback.onComplete(0));
            return;
        }

        postToStorage(messagesStorage, () -> {
            Set<String> uniqueKeys = new HashSet<>();
            Map<Long, ArrayList<Integer>> notifyByChannel = new HashMap<>();
            Map<Long, ArrayList<Integer>> pruneByDialog = new HashMap<>();
            Map<Long, Long> dialogToChannel = new HashMap<>();

            try {
                SQLiteDatabase db = messagesStorage.getDatabase();
                if (db != null) {
                    for (int i = 0; i < 2; ++i) {
                        final String table = i == 0 ? "messages_v2" : "messages_topics";
                        final boolean isTopics = (i == 1);
                        List<long[]> rowsToDelete = new ArrayList<>();

                        SQLiteCursor cursor = null;
                        try {
                            String query = isTopics
                                    ? "SELECT data, mid, uid FROM messages_topics"
                                    : "SELECT data, mid, uid, is_channel FROM messages_v2";
                            cursor = db.queryFinalized(query, new Object[]{});
                            if (cursor != null) {
                                while (cursor.next()) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data == null || data.nativeByteBuffer == null) continue;
                                    int flags = 0;
                                    try {
                                        data.position(4);
                                        flags = data.readInt32(true);
                                    } finally {
                                        data.reuse();
                                    }
                                    if ((flags & ShowDeletedMessages.FLAG_DELETED) != 0) {
                                        int mid = cursor.intValue(1);
                                        long uid = cursor.longValue(2);
                                        long channelId = (!isTopics && cursor.intValue(3) != 0 && uid < 0) ? -uid : 0L;
                                        rowsToDelete.add(new long[]{uid, mid});
                                        if (channelId != 0L) {
                                            dialogToChannel.put(uid, channelId);
                                        }
                                        if (uniqueKeys.add(uid + "#" + mid)) {
                                            notifyByChannel.computeIfAbsent(channelId, k -> new ArrayList<>()).add(mid);
                                            pruneByDialog.computeIfAbsent(uid, k -> new ArrayList<>()).add(mid);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            if (!isTopics) Logger.e(e);
                        } finally {
                            if (cursor != null) {
                                try { cursor.dispose(); } catch (Throwable ignored) {}
                            }
                        }

                        if (!rowsToDelete.isEmpty()) {
                            SQLitePreparedStatement state = null;
                            try {
                                state = db.executeFast("DELETE FROM " + table + " WHERE uid = ? AND mid = ?");
                                if (state != null) {
                                    for (long[] row : rowsToDelete) {
                                        state.requery();
                                        state.bindLong(1, row[0]);
                                        state.bindInteger(2, (int) row[1]);
                                        state.step();
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            } finally {
                                if (state != null) {
                                    try { state.dispose(); } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Logger.e(e);
            }

            final int clearedCount = uniqueKeys.size();
            final int account = UserConfig.getSelectedAccount();
            mainHandler.post(() -> {
                for (Map.Entry<Long, ArrayList<Integer>> e : pruneByDialog.entrySet()) {
                    long uid = e.getKey();
                    long chId = dialogToChannel.getOrDefault(uid, 0L);
                    pruneInMemoryMessages(uid, chId, e.getValue());
                }
                for (Map.Entry<Long, ArrayList<Integer>> e : notifyByChannel.entrySet()) {
                    NotificationCenter.postMessagesDeleted(account, e.getValue(), e.getKey());
                }
                if (callback != null) {
                    callback.onComplete(clearedCount);
                }
            });
        });
    }

    private static void pruneInMemoryMessages(long dialogId, long channelId, ArrayList<Integer> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) return;
        try {
            MessagesController mc = MessagesController.getMessagesController();
            if (mc == null || mc.getMessagesControllerObject() == null) return;

            if (channelId == 0) {
                SparseArray<Object> byIds = mc.getDialogMessagesByIds();
                if (byIds != null) {
                    for (int id : msgIds) {
                        byIds.remove(id);
                    }
                }
            }
            if (dialogId != 0) {
                LongSparseArray dialogMessage = mc.getDialogMessage();
                ArrayList<Object> list = dialogMessage != null ? dialogMessage.get(dialogId) : null;
                if (list != null && !list.isEmpty()) {
                    Set<Integer> targetIds = new HashSet<>(msgIds);
                    for (int i = list.size() - 1; i >= 0; i--) {
                        try {
                            int ownerId = new MessageObject(list.get(i)).getMessageOwner().getId();
                            if (targetIds.contains(ownerId)) {
                                list.remove(i);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void postToStorage(MessagesStorage messagesStorage, Runnable task) {
        try {
            DispatchQueue queue = messagesStorage.getStorageQueue();
            if (queue != null) {
                queue.postRunnable(task);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            storage.post(task);
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static Handler getStorage() {
        return storage;
    }

}
