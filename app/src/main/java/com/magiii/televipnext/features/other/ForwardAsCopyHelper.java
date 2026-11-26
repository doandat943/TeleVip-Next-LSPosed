package com.magiii.televipnext.features.other;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.messenger.FileLoader;
import com.magiii.televipnext.virtuals.messenger.MessagesController;
import com.magiii.televipnext.virtuals.messenger.UserConfig;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

/**
 * Intercepts forwarding of messages from restricted (noforwards) chats/channels when
 * Removes Content-Saving is enabled and re-sends them as fresh copies instead of calling
 * TL_messages_forwardMessages (which Telegram's server rejects with CHAT_FORWARDS_RESTRICTED).
 */
public class ForwardAsCopyHelper {

    private static final int RESULT_OK = 0;
    private static final int RESULT_MISSING_MEDIA = 1;
    private static final int RESULT_SEND_FAILED = 2;

    private static final int[] MEDIA_DIRS = {4, 5, 2, 3, 0, 1, 6, 100, 101};

    private static final long POLL_INTERVAL_MS = 400L;
    private static final long RETRIGGER_DOWNLOAD_MS = 15_000L;
    private static final long DEFAULT_DOWNLOAD_TIMEOUT_MS = 90_000L;
    private static final long LARGE_DOWNLOAD_TIMEOUT_MS = 180_000L;
    private static final long LARGE_MEDIA_THRESHOLD_BYTES = 20_000_000L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ScheduledExecutorService DOWNLOAD_POLL_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TeleVip-ForwardDownload");
                t.setDaemon(true);
                return t;
            });

    private static boolean initialized = false;
    private static volatile Field cachedOwnerField = null;
    private static volatile boolean ownerFieldScanned = false;
    private static volatile Method cachedLoadDocMethod = null;
    private static volatile Method cachedLoadImageMethod = null;
    private static volatile Method cachedGetForPhotoMethod = null;
    private static volatile Method cachedGetDirMethod = null;
    private static volatile boolean getDirMethodScanned = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> smhClass = ClassLoad.getClass(ClassNames.SEND_MESSAGES_HELPER, Utils.classLoader, false);
            if (smhClass == null) return;

            String fwdName = Obfuscate.getMethodName("SendMessagesHelper", "sendMessageForward");
            if (fwdName == null || "sendMessageForward".equals(fwdName)) {
                fwdName = "sendMessage";
            }

            XposedCompat.hookAllMethods(smhClass, fwdName, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (ConfigManager.removesContentSaving == null || !ConfigManager.removesContentSaving.isEnable()) {
                        return;
                    }
                    if (param.args == null || param.args.length < 6) return;
                    if (!(param.args[0] instanceof ArrayList) || !(param.args[1] instanceof Long)
                            || !(param.args[2] instanceof Boolean) || !(param.args[3] instanceof Boolean)) {
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    ArrayList<Object> messages = (ArrayList<Object>) param.args[0];
                    if (messages == null || messages.isEmpty()) return;

                    long peer = (Long) param.args[1];
                    boolean forwardFromMyName = (Boolean) param.args[2];
                    boolean hideCaption = (Boolean) param.args[3];
                    boolean notify = param.args.length > 4 && param.args[4] instanceof Boolean ? (Boolean) param.args[4] : true;
                    int scheduleDate = param.args.length > 5 && param.args[5] instanceof Integer ? (Integer) param.args[5] : 0;
                    int scheduleRepeat = param.args.length > 6 && param.args[6] instanceof Integer ? (Integer) param.args[6] : 0;
                    Object replyToTopMsg = param.args.length > 7 && !(param.args[7] instanceof Integer) ? param.args[7] : null;

                    ArrayList<Object> restricted = new ArrayList<>();
                    ArrayList<Object> normal = new ArrayList<>();
                    for (Object msgObj : messages) {
                        if (msgObj == null) continue;
                        if (isRestrictedMessage(msgObj)) {
                            restricted.add(msgObj);
                        } else {
                            normal.add(msgObj);
                        }
                    }

                    if (restricted.isEmpty()) return;

                    sendCopies(param.thisObject, restricted, peer, forwardFromMyName, hideCaption,
                            notify, scheduleDate, scheduleRepeat, replyToTopMsg);

                    if (normal.isEmpty()) {
                        param.setResult(0);
                    } else {
                        param.args[0] = normal;
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static boolean isRestrictedMessage(Object msgObj) {
        try {
            Object owner = getOwner(msgObj);
            if (owner != null) {
                try {
                    if (ReflectHelper.getBooleanField(owner, "noforwards")) return true;
                } catch (Throwable ignored) {}
            }
            long dialogId = getMessageDialogId(msgObj, owner);
            Object mc = MessagesController.getMessagesController().getMessagesControllerObject();
            if (dialogId < 0) {
                Object chat = getChatById(mc, -dialogId);
                if (chat != null && getBooleanField(chat, "noforwards")) {
                    return true;
                }
            } else if (dialogId > 0) {
                Object userFull = getUserFullById(mc, dialogId);
                if (userFull != null && (getBooleanField(userFull, "noforwards_peer_enabled")
                        || getBooleanField(userFull, "noforwards_my_enabled"))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static long getMessageDialogId(Object msgObj, Object owner) {
        try {
            com.magiii.televipnext.virtuals.messenger.MessageObject mo =
                    new com.magiii.televipnext.virtuals.messenger.MessageObject(msgObj);
            long did = mo.getDialogId();
            if (did != 0L) return did;
        } catch (Throwable ignored) {}
        if (owner != null) {
            try {
                long did = ReflectHelper.getLongField(owner, Obfuscate.getFieldName("Message", "dialog_id"));
                if (did != 0L) return did;
            } catch (Throwable ignored) {}
            Object peerId = getField(owner, "peer_id");
            if (peerId != null) {
                try {
                    long channelId = ReflectHelper.getLongField(peerId, "channel_id");
                    if (channelId != 0L) return -channelId;
                } catch (Throwable ignored) {}
                try {
                    long chatId = ReflectHelper.getLongField(peerId, "chat_id");
                    if (chatId != 0L) return -chatId;
                } catch (Throwable ignored) {}
                try {
                    long userId = ReflectHelper.getLongField(peerId, "user_id");
                    if (userId != 0L) return userId;
                } catch (Throwable ignored) {}
            }
        }
        return 0L;
    }

    private static Object getChatById(Object mc, long chatId) {
        if (mc == null) return null;
        String getChatName = Obfuscate.getMethodName("MessagesController", "getChat");
        if (getChatName != null && !"getChat".equals(getChatName)) {
            Method mBoxed = ReflectHelper.findMethodExactIfExists(mc.getClass(), getChatName, Long.class);
            if (mBoxed != null) {
                try { return mBoxed.invoke(mc, Long.valueOf(chatId)); } catch (Throwable ignored) {}
            }
            Method mLong = ReflectHelper.findMethodExactIfExists(mc.getClass(), getChatName, long.class);
            if (mLong != null) {
                try { return mLong.invoke(mc, chatId); } catch (Throwable ignored) {}
            }
        }
        Method mBoxedFb = ReflectHelper.findMethodExactIfExists(mc.getClass(), "getChat", Long.class);
        if (mBoxedFb != null) {
            try { return mBoxedFb.invoke(mc, Long.valueOf(chatId)); } catch (Throwable ignored) {}
        }
        Method mLongFb = ReflectHelper.findMethodExactIfExists(mc.getClass(), "getChat", long.class);
        if (mLongFb != null) {
            try { return mLongFb.invoke(mc, chatId); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object getUserFullById(Object mc, long userId) {
        if (mc == null) return null;
        String getFull = Obfuscate.getMethodName("MessagesController", "getUserFull");
        if (getFull != null && !"getUserFull".equals(getFull)) {
            Method mLong = ReflectHelper.findMethodExactIfExists(mc.getClass(), getFull, long.class);
            if (mLong != null) {
                try { return mLong.invoke(mc, userId); } catch (Throwable ignored) {}
            }
            Method mBoxed = ReflectHelper.findMethodExactIfExists(mc.getClass(), getFull, Long.class);
            if (mBoxed != null) {
                try { return mBoxed.invoke(mc, Long.valueOf(userId)); } catch (Throwable ignored) {}
            }
        }
        Method mLongFb = ReflectHelper.findMethodExactIfExists(mc.getClass(), "getUserFull", long.class);
        if (mLongFb != null) {
            try { return mLongFb.invoke(mc, userId); } catch (Throwable ignored) {}
        }
        Class<?> userFullCls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$UserFull", Utils.classLoader);
        if (userFullCls != null) {
            for (Method m : mc.getClass().getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (!Modifier.isStatic(m.getModifiers()) && pt.length == 1
                        && (pt[0] == long.class || pt[0] == Long.class)
                        && userFullCls.isAssignableFrom(m.getReturnType())) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(mc, pt[0] == Long.class ? Long.valueOf(userId) : userId);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private static Object getUserById(Object mc, long userId) {
        if (mc == null) return null;
        String getUserName = Obfuscate.getMethodName("MessagesController", "getUser");
        if (getUserName != null && !"getUser".equals(getUserName)) {
            Method mBoxed = ReflectHelper.findMethodExactIfExists(mc.getClass(), getUserName, Long.class);
            if (mBoxed != null) {
                try { return mBoxed.invoke(mc, Long.valueOf(userId)); } catch (Throwable ignored) {}
            }
            Method mLong = ReflectHelper.findMethodExactIfExists(mc.getClass(), getUserName, long.class);
            if (mLong != null) {
                try { return mLong.invoke(mc, userId); } catch (Throwable ignored) {}
            }
        }
        Method mBoxedFb = ReflectHelper.findMethodExactIfExists(mc.getClass(), "getUser", Long.class);
        if (mBoxedFb != null) {
            try { return mBoxedFb.invoke(mc, Long.valueOf(userId)); } catch (Throwable ignored) {}
        }
        Method mLongFb = ReflectHelper.findMethodExactIfExists(mc.getClass(), "getUser", long.class);
        if (mLongFb != null) {
            try { return mLongFb.invoke(mc, userId); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void sendCopies(Object smh, List<Object> messages, long peer,
                                   boolean forwardFromMyName, boolean hideCaption,
                                   boolean notify, int scheduleDate, int scheduleRepeat,
                                   Object replyToTopMsg) {
        // Group consecutive album items sharing non-zero grouped_id
        Map<Long, List<Object>> albumBuckets = new LinkedHashMap<>();
        long syntheticKey = -1L;

        for (Object msgObj : messages) {
            Object owner = getOwner(msgObj);
            long groupedId = 0L;
            if (owner != null) {
                try {
                    groupedId = ReflectHelper.getLongField(owner, "grouped_id");
                } catch (Throwable ignored) {}
            }
            long key = groupedId != 0L ? groupedId : (syntheticKey--);
            List<Object> bucket = albumBuckets.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                albumBuckets.put(key, bucket);
            }
            bucket.add(msgObj);
        }

        List<List<Object>> orderedBuckets = new ArrayList<>(albumBuckets.values());
        if (orderedBuckets.isEmpty()) return;

        int account = UserConfig.getSelectedAccount();

        int idx = 0;
        boolean missingMediaNotified = false;
        boolean sendFailedNotified = false;
        while (idx < orderedBuckets.size() && isBucketReady(orderedBuckets.get(idx), account)) {
            int r = sendBucket(smh, account, orderedBuckets.get(idx), peer, forwardFromMyName, hideCaption,
                    notify, scheduleDate, scheduleRepeat, replyToTopMsg);
            if (r == RESULT_MISSING_MEDIA) missingMediaNotified = true;
            else if (r == RESULT_SEND_FAILED) sendFailedNotified = true;
            idx++;
        }

        if (idx >= orderedBuckets.size()) {
            notifyErrorToast(missingMediaNotified, sendFailedNotified);
            return;
        }

        boolean startedAny = false;
        for (int i = idx; i < orderedBuckets.size(); i++) {
            List<Object> b = orderedBuckets.get(i);
            if (!isBucketReady(b, account)) {
                if (triggerDownloadsForBucket(b, account)) {
                    startedAny = true;
                }
            }
        }

        if (!startedAny) {
            for (int i = idx; i < orderedBuckets.size(); i++) {
                int r = sendBucket(smh, account, orderedBuckets.get(i), peer, forwardFromMyName, hideCaption,
                        notify, scheduleDate, scheduleRepeat, replyToTopMsg);
                if (r == RESULT_MISSING_MEDIA) missingMediaNotified = true;
                else if (r == RESULT_SEND_FAILED) sendFailedNotified = true;
            }
            notifyErrorToast(missingMediaNotified, sendFailedNotified);
            return;
        }

        showToast(Translator.get(Keys.DownloadingMediaToForward));
        PendingForwardTask task = new PendingForwardTask(
                smh, account, orderedBuckets, idx, peer, forwardFromMyName, hideCaption,
                notify, scheduleDate, scheduleRepeat, replyToTopMsg);
        task.start();
    }

    private static final class PendingForwardTask {
        final Object smh;
        final int account;
        final List<List<Object>> buckets;
        final long peer;
        final boolean forwardFromMyName;
        final boolean hideCaption;
        final boolean notify;
        final int scheduleDate;
        final int scheduleRepeat;
        final Object replyToTopMsg;

        int nextIdx;
        long bucketStartMs;
        boolean retriggered;
        boolean missingToastShown;
        boolean failedToastShown;
        volatile ScheduledFuture<?> future;

        PendingForwardTask(Object smh, int account, List<List<Object>> buckets, int startIdx, long peer,
                           boolean forwardFromMyName, boolean hideCaption, boolean notify,
                           int scheduleDate, int scheduleRepeat, Object replyToTopMsg) {
            this.smh = smh;
            this.account = account;
            this.buckets = buckets;
            this.nextIdx = startIdx;
            this.peer = peer;
            this.forwardFromMyName = forwardFromMyName;
            this.hideCaption = hideCaption;
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.scheduleRepeat = scheduleRepeat;
            this.replyToTopMsg = replyToTopMsg;
            this.bucketStartMs = System.currentTimeMillis();
        }

        void start() {
            future = DOWNLOAD_POLL_EXECUTOR.scheduleAtFixedRate(
                    this::pollTick, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void pollTick() {
            try {
                while (nextIdx < buckets.size()) {
                    List<Object> head = buckets.get(nextIdx);
                    if (isBucketReady(head, account)) {
                        dispatchBucket(head);
                        advance();
                        continue;
                    }

                    long elapsed = System.currentTimeMillis() - bucketStartMs;
                    long timeoutMs = hasLargeMedia(head) ? LARGE_DOWNLOAD_TIMEOUT_MS : DEFAULT_DOWNLOAD_TIMEOUT_MS;
                    if (!retriggered && elapsed >= RETRIGGER_DOWNLOAD_MS) {
                        retriggered = true;
                        MAIN_HANDLER.post(() -> triggerDownloadsForBucket(head, account));
                    }
                    if (elapsed >= timeoutMs) {
                        dispatchBucket(head);
                        advance();
                        continue;
                    }
                    break;
                }

                if (nextIdx >= buckets.size()) {
                    ScheduledFuture<?> f = future;
                    if (f != null) f.cancel(false);
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        }

        private void dispatchBucket(List<Object> head) {
            MAIN_HANDLER.post(() -> {
                int r = sendBucket(smh, account, head, peer, forwardFromMyName, hideCaption,
                        notify, scheduleDate, scheduleRepeat, replyToTopMsg);
                if (r == RESULT_MISSING_MEDIA && !missingToastShown) {
                    missingToastShown = true;
                    showToast(Translator.get(Keys.DownloadMediaForForwardFailed));
                } else if (r == RESULT_SEND_FAILED && !failedToastShown) {
                    failedToastShown = true;
                    showToast(Translator.get(Keys.ForwardRestrictedFailed));
                }
            });
        }

        private void advance() {
            nextIdx++;
            bucketStartMs = System.currentTimeMillis();
            retriggered = false;
        }
    }

    private static void notifyErrorToast(boolean missingMedia, boolean sendFailed) {
        if (missingMedia) {
            showToast(Translator.get(Keys.DownloadMediaForForwardFailed));
        } else if (sendFailed) {
            showToast(Translator.get(Keys.ForwardRestrictedFailed));
        }
    }

    private static int sendBucket(Object smh, int account, List<Object> bucket, long peer,
                                  boolean forwardFromMyName, boolean hideCaption,
                                  boolean notify, int scheduleDate, int scheduleRepeat,
                                  Object replyToTopMsg) {
        if (bucket == null || bucket.isEmpty()) return RESULT_OK;
        if (bucket.size() > 1) {
            if (sendMediaAlbum(account, bucket, peer, forwardFromMyName, hideCaption,
                    notify, scheduleDate, scheduleRepeat, replyToTopMsg)) {
                return RESULT_OK;
            }
        }
        int status = RESULT_OK;
        for (Object m : bucket) {
            int r = sendSingleCopy(smh, account, m, peer, forwardFromMyName, hideCaption,
                    notify, scheduleDate, scheduleRepeat, replyToTopMsg);
            if (r == RESULT_MISSING_MEDIA) {
                status = RESULT_MISSING_MEDIA;
            } else if (r == RESULT_SEND_FAILED && status == RESULT_OK) {
                status = RESULT_SEND_FAILED;
            }
        }
        return status;
    }

    private static boolean isBucketReady(List<Object> bucket, int account) {
        if (bucket == null || bucket.isEmpty()) return true;
        for (Object msgObj : bucket) {
            if (!isMediaReadyForMessage(msgObj, account)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLargeMedia(List<Object> bucket) {
        if (bucket == null) return false;
        for (Object msgObj : bucket) {
            Object owner = getOwner(msgObj);
            Object media = getField(owner, "media");
            Object doc = getField(media, "document");
            if (doc != null && getLongOrIntField(doc, "size") > LARGE_MEDIA_THRESHOLD_BYTES) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMediaReadyForMessage(Object msgObj, int account) {
        Object owner = getOwner(msgObj);
        if (owner == null) return true;
        Object media = getField(owner, "media");
        if (isEmptyOrWebPageMedia(media)) return true;

        Object photo = getField(media, "photo");
        if (photo != null && !photo.getClass().getSimpleName().contains("Empty")) {
            if (checkAttachPath(owner) != null) return true;
            FileLoader fl = FileLoader.getInstance(account);
            List<Object> sizesDesc = getPhotoSizesDesc(photo);
            if (!sizesDesc.isEmpty()) {
                return resolveExistingFile(callGetPathToAttach(fl, sizesDesc.get(0))) != null;
            }
            return resolvePhotoFile(account, owner, photo) != null;
        }

        Object document = getField(media, "document");
        if (document != null && !document.getClass().getSimpleName().contains("Empty")) {
            if (isStickerMessage(msgObj, document)) return true;
            File f = resolveDocumentFile(account, owner, document);
            if (!existsNonEmpty(f)) return false;
            long expectedSize = getLongOrIntField(document, "size");
            return expectedSize <= 0L || f.length() >= expectedSize;
        }
        return true;
    }

    private static boolean triggerDownloadsForBucket(List<Object> bucket, int account) {
        if (bucket == null || bucket.isEmpty()) return false;
        boolean triggered = false;
        for (Object msgObj : bucket) {
            if (isMediaReadyForMessage(msgObj, account)) continue;
            Object owner = getOwner(msgObj);
            Object media = getField(owner, "media");
            if (media == null) continue;

            Object photo = getField(media, "photo");
            if (photo != null && !photo.getClass().getSimpleName().contains("Empty")) {
                List<Object> sizesDesc = getPhotoSizesDesc(photo);
                Object bestSize = !sizesDesc.isEmpty() ? sizesDesc.get(0) : null;
                if (bestSize != null && triggerPhotoDownload(account, photo, bestSize, msgObj)) {
                    triggered = true;
                }
                continue;
            }

            Object document = getField(media, "document");
            if (document != null && !document.getClass().getSimpleName().contains("Empty")
                    && !isStickerMessage(msgObj, document)) {
                if (triggerDocumentDownload(account, document, msgObj)) {
                    triggered = true;
                }
            }
        }
        return triggered;
    }

    private static boolean triggerDocumentDownload(int account, Object document, Object parentMsgObj) {
        try {
            FileLoader fl = FileLoader.getInstance(account);
            Object flObj = fl.getFileLoaderObject();
            if (flObj == null) return false;
            Class<?> flCls = flObj.getClass();
            String mapped = Obfuscate.getMethodName("FileLoader", "loadFileDocument");
            Method target = findFileLoaderDocumentMethod(flCls, mapped, document.getClass());
            if (target == null) return false;
            target.setAccessible(true);
            target.invoke(flObj, document, parentMsgObj, 3, 0);
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean triggerPhotoDownload(int account, Object photo, Object photoSize, Object parentMsgObj) {
        try {
            Object imgLoc = createImageLocationForPhoto(photoSize, photo);
            if (imgLoc == null) return false;
            FileLoader fl = FileLoader.getInstance(account);
            Object flObj = fl.getFileLoaderObject();
            if (flObj == null) return false;
            Class<?> flCls = flObj.getClass();
            String mapped = Obfuscate.getMethodName("FileLoader", "loadFileImage");
            Method target = findFileLoaderImageMethod(flCls, mapped, imgLoc.getClass());
            if (target == null) return false;
            target.setAccessible(true);
            target.invoke(flObj, imgLoc, parentMsgObj, "jpg", 3, 1);
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static Object createImageLocationForPhoto(Object photoSize, Object photo) {
        try {
            Class<?> imgLocCls = ClassLoad.getClass(ClassNames.IMAGE_LOCATION, Utils.classLoader, false);
            if (imgLocCls == null) return null;
            String mapped = Obfuscate.getMethodName("ImageLocation", "getForPhoto");
            Method target = findImageLocationFactoryMethod(imgLocCls, mapped, photoSize.getClass(), photo.getClass());
            if (target == null) return null;
            target.setAccessible(true);
            return target.invoke(null, photoSize, photo);
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static Method findFileLoaderDocumentMethod(Class<?> flCls, String mapped, Class<?> docCls) {
        if (cachedLoadDocMethod != null) return cachedLoadDocMethod;
        Method fallbackByName = null;
        Method fallbackBySig = null;
        for (Method m : flCls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 4 && pt[0].isAssignableFrom(docCls) && pt[1] == Object.class
                    && pt[2] == int.class && pt[3] == int.class) {
                if (mapped != null && m.getName().equals(mapped)) {
                    cachedLoadDocMethod = m;
                    return m;
                }
                if ("loadFile".equals(m.getName()) && fallbackByName == null) fallbackByName = m;
                if (fallbackBySig == null) fallbackBySig = m;
            }
        }
        Method res = fallbackByName != null ? fallbackByName : fallbackBySig;
        if (res != null) cachedLoadDocMethod = res;
        return res;
    }

    private static Method findFileLoaderImageMethod(Class<?> flCls, String mapped, Class<?> imgLocCls) {
        if (cachedLoadImageMethod != null) return cachedLoadImageMethod;
        Method fallbackByName = null;
        Method fallbackBySig = null;
        for (Method m : flCls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 5 && pt[0].isAssignableFrom(imgLocCls) && pt[1] == Object.class
                    && pt[2] == String.class && pt[3] == int.class && pt[4] == int.class) {
                if (mapped != null && m.getName().equals(mapped)) {
                    cachedLoadImageMethod = m;
                    return m;
                }
                if ("loadFile".equals(m.getName()) && fallbackByName == null) fallbackByName = m;
                if (fallbackBySig == null) fallbackBySig = m;
            }
        }
        Method res = fallbackByName != null ? fallbackByName : fallbackBySig;
        if (res != null) cachedLoadImageMethod = res;
        return res;
    }

    private static Method findImageLocationFactoryMethod(Class<?> imgLocCls, String mapped,
                                                         Class<?> photoSizeCls, Class<?> photoCls) {
        if (cachedGetForPhotoMethod != null) return cachedGetForPhotoMethod;
        Method fallbackByName = null;
        Method fallbackBySig = null;
        for (Method m : imgLocCls.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !imgLocCls.isAssignableFrom(m.getReturnType())) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 2 && pt[0].isAssignableFrom(photoSizeCls) && pt[1].isAssignableFrom(photoCls)) {
                if (mapped != null && m.getName().equals(mapped)) {
                    cachedGetForPhotoMethod = m;
                    return m;
                }
                if ("getForPhoto".equals(m.getName()) && fallbackByName == null) fallbackByName = m;
                if (fallbackBySig == null) fallbackBySig = m;
            }
        }
        Method res = fallbackByName != null ? fallbackByName : fallbackBySig;
        if (res != null) cachedGetForPhotoMethod = res;
        return res;
    }

    private static int sendSingleCopy(Object smh, int account, Object msgObj, long peer,
                                      boolean forwardFromMyName, boolean hideCaption,
                                      boolean notify, int scheduleDate, int scheduleRepeat,
                                      Object replyToTopMsg) {
        try {
            Object owner = getOwner(msgObj);
            if (owner == null) return RESULT_OK;

            Attribution attr = buildAttribution(msgObj, owner, forwardFromMyName);
            String prefix = attr != null ? attr.prefix : null;
            Object media = getField(owner, "media");
            String rawText = getStringField(owner, "message");
            ArrayList<Object> rawEntities = getListField(owner, "entities");

            if (isEmptyOrWebPageMedia(media)) {
                String text = buildCombinedText(prefix, rawText, false);
                if (TextUtils.isEmpty(text)) return RESULT_OK;
                ArrayList<Object> entities = combineEntities(attr, rawEntities, true);
                Object webPage = media != null ? getField(media, "webpage") : null;
                boolean ok = sendTextParams(smh, text, peer, replyToTopMsg, webPage, entities, notify, scheduleDate, scheduleRepeat);
                if (!ok) Logger.w("ForwardAsCopy: sendTextParams failed peer=" + peer);
                return ok ? RESULT_OK : RESULT_SEND_FAILED;
            }

            Object photo = getField(media, "photo");
            if (photo != null && !photo.getClass().getSimpleName().contains("Empty")) {
                File f = resolvePhotoFile(account, owner, photo);
                if (f == null || !f.exists()) {
                    Logger.w("ForwardAsCopy: photo file missing f=" + f);
                    return RESULT_MISSING_MEDIA;
                }
                String caption = buildCombinedText(prefix, hideCaption ? "" : rawText, true);
                ArrayList<Object> entities = combineEntities(attr, rawEntities, !hideCaption);
                boolean ok = sendPhotoFile(account, f, peer, replyToTopMsg, caption, entities, notify, scheduleDate);
                if (!ok) {
                    ok = sendSingleFileAsMedia(account, f, false, false, peer, replyToTopMsg, caption, entities,
                            notify, scheduleDate, scheduleRepeat);
                }
                if (!ok) Logger.w("ForwardAsCopy: sendPhotoFile failed path=" + f.getAbsolutePath());
                return ok ? RESULT_OK : RESULT_SEND_FAILED;
            }

            Object document = getField(media, "document");
            if (document != null && !document.getClass().getSimpleName().contains("Empty")) {
                if (isStickerMessage(msgObj, document)) {
                    if (attr != null) {
                        sendTextParams(smh, attr.prefix.trim(), peer, replyToTopMsg, null, attr.entities, notify, scheduleDate, scheduleRepeat);
                    }
                    boolean ok = sendStickerDocument(smh, document, peer, replyToTopMsg, notify, scheduleDate, scheduleRepeat);
                    if (!ok) Logger.w("ForwardAsCopy: sendStickerDocument failed");
                    return ok ? RESULT_OK : RESULT_SEND_FAILED;
                }
                File f = resolveDocumentFile(account, owner, document);
                if (f == null || !f.exists()) {
                    Logger.w("ForwardAsCopy: document file missing f=" + f);
                    return RESULT_MISSING_MEDIA;
                }
                String caption = buildCombinedText(prefix, hideCaption ? "" : rawText, true);
                ArrayList<Object> entities = combineEntities(attr, rawEntities, !hideCaption);
                if (isVideoDocument(document)) {
                    if (sendSingleVideoAsMedia(account, f, peer, replyToTopMsg, caption, entities,
                            notify, scheduleDate, scheduleRepeat)) {
                        return RESULT_OK;
                    }
                } else {
                    if (sendSingleDocumentAsMedia(account, f, peer, replyToTopMsg, caption, entities,
                            notify, scheduleDate, scheduleRepeat)) {
                        return RESULT_OK;
                    }
                }
                String mime = getStringField(document, "mime_type");
                String fallbackCap = buildCombinedText(attr != null ? attr.plainFallback : null, hideCaption ? "" : rawText, true);
                boolean ok = sendDocumentFile(account, f, mime, peer, replyToTopMsg, fallbackCap, notify);
                if (!ok) Logger.w("ForwardAsCopy: sendDocumentFile failed path=" + f.getAbsolutePath());
                return ok ? RESULT_OK : RESULT_SEND_FAILED;
            }

            String mediaClassName = media.getClass().getSimpleName();
            if (mediaClassName.contains("Geo") || mediaClassName.contains("Venue")) {
                if (attr != null) {
                    sendTextParams(smh, attr.prefix.trim(), peer, replyToTopMsg, null, attr.entities, notify, scheduleDate, scheduleRepeat);
                }
                return sendMediaParams(smh, media, peer, replyToTopMsg, notify, scheduleDate) ? RESULT_OK : RESULT_SEND_FAILED;
            }

            String phone = getStringField(media, "phone_number");
            if (phone != null) {
                if (attr != null) {
                    sendTextParams(smh, attr.prefix.trim(), peer, replyToTopMsg, null, attr.entities, notify, scheduleDate, scheduleRepeat);
                }
                return sendContactParams(smh, media, phone, peer, replyToTopMsg, notify, scheduleDate, scheduleRepeat) ? RESULT_OK : RESULT_SEND_FAILED;
            }

            // Fallback: send text/caption if present
            if (!TextUtils.isEmpty(rawText)) {
                String text = buildCombinedText(prefix, rawText, false);
                ArrayList<Object> entities = combineEntities(attr, rawEntities, true);
                return sendTextParams(smh, text, peer, replyToTopMsg, null, entities, notify, scheduleDate, scheduleRepeat)
                        ? RESULT_OK : RESULT_SEND_FAILED;
            }
        } catch (Throwable t) {
            Logger.e(t);
            return RESULT_SEND_FAILED;
        }
        return RESULT_OK;
    }

    private static boolean sendMediaAlbum(int account, List<Object> bucket, long peer,
                                          boolean forwardFromMyName, boolean hideCaption,
                                          boolean notify, int scheduleDate, int scheduleRepeat,
                                          Object replyToTopMsg) {
        try {
            Class<?> smiClass = ClassLoad.getClass(ClassNames.SENDING_MEDIA_INFO, Utils.classLoader, false);
            if (smiClass == null) return false;

            String pathField = Obfuscate.getFieldName("SendingMediaInfo", "path");
            String captionField = Obfuscate.getFieldName("SendingMediaInfo", "caption");
            String entitiesField = Obfuscate.getFieldName("SendingMediaInfo", "entities");
            String isVideoField = Obfuscate.getFieldName("SendingMediaInfo", "isVideo");

            ArrayList<Object> mediaList = new ArrayList<>();
            for (Object msgObj : bucket) {
                Object owner = getOwner(msgObj);
                if (owner == null) continue;
                Object media = getField(owner, "media");
                if (media == null) return false;

                File f = null;
                boolean isVideo = false;
                Object photo = getField(media, "photo");
                Object doc = getField(media, "document");
                if (photo != null && !photo.getClass().getSimpleName().contains("Empty")) {
                    f = resolvePhotoFile(account, owner, photo);
                } else if (doc != null && !doc.getClass().getSimpleName().contains("Empty")) {
                    f = resolveDocumentFile(account, owner, doc);
                    isVideo = isVideoDocument(doc);
                }
                if (f == null || !f.exists()) return false;

                Object smi = smiClass.newInstance();
                ReflectHelper.setObjectField(smi, pathField, f.getAbsolutePath());
                ReflectHelper.setBooleanField(smi, isVideoField, isVideo);

                Attribution attr = mediaList.isEmpty() ? buildAttribution(msgObj, owner, forwardFromMyName) : null;
                String prefix = attr != null ? attr.prefix : null;
                String rawCap = hideCaption ? "" : getStringField(owner, "message");
                String caption = buildCombinedText(prefix, rawCap, true);
                if (!TextUtils.isEmpty(caption)) {
                    ReflectHelper.setObjectField(smi, captionField, caption);
                }
                ArrayList<Object> ents = combineEntities(attr, getListField(owner, "entities"), !hideCaption);
                if (ents != null && !ents.isEmpty()) {
                    ReflectHelper.setObjectField(smi, entitiesField, ents);
                }
                mediaList.add(smi);
            }

            if (mediaList.isEmpty()) return false;
            return invokePrepareSendingMedia(account, mediaList, peer, replyToTopMsg, false, true, notify, scheduleDate, scheduleRepeat);
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean sendSingleVideoAsMedia(int account, File file, long peer, Object replyToTopMsg,
                                                  String caption, ArrayList<Object> entities,
                                                  boolean notify, int scheduleDate, int scheduleRepeat) {
        return sendSingleFileAsMedia(account, file, true, false, peer, replyToTopMsg, caption, entities, notify, scheduleDate, scheduleRepeat);
    }

    private static boolean sendSingleDocumentAsMedia(int account, File file, long peer, Object replyToTopMsg,
                                                     String caption, ArrayList<Object> entities,
                                                     boolean notify, int scheduleDate, int scheduleRepeat) {
        return sendSingleFileAsMedia(account, file, false, true, peer, replyToTopMsg, caption, entities, notify, scheduleDate, scheduleRepeat);
    }

    private static boolean sendSingleFileAsMedia(int account, File file, boolean isVideo, boolean forceDocument,
                                                 long peer, Object replyToTopMsg,
                                                 String caption, ArrayList<Object> entities,
                                                 boolean notify, int scheduleDate, int scheduleRepeat) {
        try {
            Class<?> smiClass = ClassLoad.getClass(ClassNames.SENDING_MEDIA_INFO, Utils.classLoader, false);
            if (smiClass == null) return false;
            Object smi = smiClass.newInstance();
            ReflectHelper.setObjectField(smi, Obfuscate.getFieldName("SendingMediaInfo", "path"), file.getAbsolutePath());
            ReflectHelper.setBooleanField(smi, Obfuscate.getFieldName("SendingMediaInfo", "isVideo"), isVideo);
            if (!TextUtils.isEmpty(caption)) {
                ReflectHelper.setObjectField(smi, Obfuscate.getFieldName("SendingMediaInfo", "caption"), caption);
            }
            if (entities != null && !entities.isEmpty()) {
                ReflectHelper.setObjectField(smi, Obfuscate.getFieldName("SendingMediaInfo", "entities"), entities);
            }
            ArrayList<Object> mediaList = new ArrayList<>(1);
            mediaList.add(smi);
            return invokePrepareSendingMedia(account, mediaList, peer, replyToTopMsg, forceDocument, false, notify, scheduleDate, scheduleRepeat);
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean sendTextParams(Object smh, String text, long peer, Object replyToTopMsg,
                                          Object webPage, ArrayList<Object> entities,
                                          boolean notify, int scheduleDate, int scheduleRepeat) {
        Class<?> paramsCls = ClassLoad.getClass(ClassNames.SEND_MESSAGE_PARAMS, Utils.classLoader, false);
        if (paramsCls == null) return false;
        String ofText = Obfuscate.getMethodName("SendMessageParams", "ofText");
        if (ofText == null || "ofText".equals(ofText)) ofText = "of";

        Object params = null;
        for (Method m : paramsCls.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(ofText)
                    && paramsCls.isAssignableFrom(m.getReturnType())
                    && pt.length >= 12 && pt.length <= 16
                    && pt[0] == String.class && pt[1] == long.class) {
                Object[] args = new Object[pt.length];
                int msgObjIdx = 0;
                int boolIdx = 0;
                int intIdx = 0;
                args[0] = text;
                args[1] = peer;
                for (int i = 2; i < pt.length; i++) {
                    Class<?> t = pt[i];
                    if (t == boolean.class) {
                        if (boolIdx == 0) args[i] = webPage != null;
                        else if (boolIdx == 1) args[i] = notify;
                        else args[i] = Boolean.FALSE;
                        boolIdx++;
                    } else if (t == int.class) {
                        args[i] = (intIdx == 0) ? scheduleDate : scheduleRepeat;
                        intIdx++;
                    } else if (t == ArrayList.class) {
                        args[i] = entities;
                    } else if (webPage != null && t.isInstance(webPage)) {
                        args[i] = webPage;
                    } else if (isMessageObjectParam(t, replyToTopMsg)) {
                        if (msgObjIdx == 1) args[i] = replyToTopMsg;
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    params = m.invoke(null, args);
                    break;
                } catch (Throwable ignored) {}
            }
        }
        return invokeSendMessageParams(smh, params);
    }

    private static boolean sendMediaParams(Object smh, Object media, long peer, Object replyToTopMsg,
                                           boolean notify, int scheduleDate) {
        Class<?> paramsCls = ClassLoad.getClass(ClassNames.SEND_MESSAGE_PARAMS, Utils.classLoader, false);
        if (paramsCls == null) return false;
        String ofMedia = Obfuscate.getMethodName("SendMessageParams", "ofMedia");
        if (ofMedia == null || "ofMedia".equals(ofMedia)) ofMedia = "of";

        Object params = null;
        for (Method m : paramsCls.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(ofMedia)
                    && pt.length >= 8 && pt.length <= 10
                    && pt[0].isInstance(media) && pt[1] == long.class) {
                Object[] args = new Object[pt.length];
                args[0] = media;
                args[1] = peer;
                int msgObjIdx = 0;
                for (int i = 2; i < pt.length; i++) {
                    if (pt[i] == boolean.class) args[i] = notify;
                    else if (pt[i] == int.class) args[i] = scheduleDate;
                    else if (msgObjIdx == 1 && replyToTopMsg != null && pt[i].isInstance(replyToTopMsg)) {
                        args[i] = replyToTopMsg;
                    }
                    if (isMessageObjectParam(pt[i], replyToTopMsg)) {
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    params = m.invoke(null, args);
                    break;
                } catch (Throwable ignored) {}
            }
        }
        return invokeSendMessageParams(smh, params);
    }

    private static boolean sendContactParams(Object smh, Object media, String phone, long peer,
                                             Object replyToTopMsg, boolean notify, int scheduleDate, int scheduleRepeat) {
        try {
            Class<?> userOldCls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$TL_userContact_old2", Utils.classLoader);
            if (userOldCls == null) return false;
            Object user = userOldCls.newInstance();
            ReflectHelper.setObjectField(user, "phone", phone);
            ReflectHelper.setObjectField(user, "first_name", getStringField(media, "first_name"));
            ReflectHelper.setObjectField(user, "last_name", getStringField(media, "last_name"));
            try { ReflectHelper.setLongField(user, "id", ReflectHelper.getLongField(media, "user_id")); } catch (Throwable ignored) {}

            Class<?> paramsCls = ClassLoad.getClass(ClassNames.SEND_MESSAGE_PARAMS, Utils.classLoader, false);
            if (paramsCls == null) return false;
            String ofUser = Obfuscate.getMethodName("SendMessageParams", "ofUser");
            if (ofUser == null || "ofUser".equals(ofUser)) ofUser = "of";

            Object params = null;
            for (Method m : paramsCls.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(ofUser)
                        && pt.length >= 8 && pt.length <= 10
                        && pt[0].isInstance(user) && pt[1] == long.class) {
                    Object[] args = new Object[pt.length];
                    args[0] = user;
                    args[1] = peer;
                    int intIdx = 0;
                    int msgObjIdx = 0;
                    for (int i = 2; i < pt.length; i++) {
                        if (pt[i] == boolean.class) args[i] = notify;
                        else if (pt[i] == int.class) { args[i] = (intIdx == 0) ? scheduleDate : scheduleRepeat; intIdx++; }
                        else if (msgObjIdx == 1 && replyToTopMsg != null && pt[i].isInstance(replyToTopMsg)) {
                            args[i] = replyToTopMsg;
                        }
                        if (isMessageObjectParam(pt[i], replyToTopMsg)) {
                            msgObjIdx++;
                        }
                    }
                    m.setAccessible(true);
                    params = m.invoke(null, args);
                    break;
                }
            }
            return invokeSendMessageParams(smh, params);
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean sendStickerDocument(Object smh, Object document, long peer,
                                               Object replyToTopMsg, boolean notify, int scheduleDate, int scheduleRepeat) {
        Class<?> paramsCls = ClassLoad.getClass(ClassNames.SEND_MESSAGE_PARAMS, Utils.classLoader, false);
        if (paramsCls == null) return false;
        String ofDoc = Obfuscate.getMethodName("SendMessageParams", "ofDocument");
        if (ofDoc == null || "ofDocument".equals(ofDoc)) ofDoc = "of";

        Object params = null;
        for (Method m : paramsCls.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(ofDoc)
                    && pt.length >= 15 && pt[0].isInstance(document)) {
                Object[] args = new Object[pt.length];
                args[0] = document;
                int intIdx = 0;
                int boolIdx = 0;
                int msgObjIdx = 0;
                for (int i = 1; i < pt.length; i++) {
                    if (pt[i] == long.class) args[i] = peer;
                    else if (pt[i] == boolean.class) { args[i] = (boolIdx == 0) ? notify : Boolean.FALSE; boolIdx++; }
                    else if (pt[i] == int.class) { args[i] = (intIdx == 0) ? scheduleDate : (intIdx == 1 ? scheduleRepeat : 0); intIdx++; }
                    else if (msgObjIdx == 1 && replyToTopMsg != null && pt[i].isInstance(replyToTopMsg)) {
                        args[i] = replyToTopMsg;
                    }
                    if (isMessageObjectParam(pt[i], replyToTopMsg)) {
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    params = m.invoke(null, args);
                    break;
                } catch (Throwable ignored) {}
            }
        }
        return invokeSendMessageParams(smh, params);
    }

    private static boolean invokeSendMessageParams(Object smh, Object params) {
        if (smh == null || params == null) return false;
        String sendParamsName = Obfuscate.getMethodName("SendMessagesHelper", "sendMessageParams");
        if (sendParamsName == null || "sendMessageParams".equals(sendParamsName)) sendParamsName = "sendMessage";
        try {
            ReflectHelper.callMethod(smh, sendParamsName, params);
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean sendPhotoFile(int account, File file, long peer, Object replyToTopMsg,
                                         String caption, ArrayList<Object> entities,
                                         boolean notify, int scheduleDate) {
        Class<?> smhClass = ClassLoad.getClass(ClassNames.SEND_MESSAGES_HELPER, Utils.classLoader, false);
        Object accInst = getAccountInstance(account);
        if (smhClass == null || accInst == null) return false;

        String methodName = Obfuscate.getMethodName("SendMessagesHelper", "prepareSendingPhoto");
        if (methodName == null || "prepareSendingPhoto".equals(methodName)) methodName = "prepareSendingPhoto";

        for (Method m : smhClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName)
                    && pt.length >= 18 && pt[0].isInstance(accInst)
                    && pt[1] == String.class && pt[3] == Uri.class && pt[4] == long.class) {
                Object[] args = new Object[pt.length];
                args[0] = accInst;
                args[1] = file.getAbsolutePath();
                int boolIdx = 0;
                int intIdx = 0;
                int listIdx = 0;
                int longIdx = 0;
                int msgObjIdx = 0;
                for (int i = 2; i < pt.length; i++) {
                    Class<?> t = pt[i];
                    if (t == long.class) {
                        args[i] = (longIdx == 0) ? peer : 0L;
                        longIdx++;
                    } else if (t == boolean.class) {
                        args[i] = (boolIdx == 0) ? notify : Boolean.FALSE;
                        boolIdx++;
                    } else if (t == int.class) {
                        args[i] = (intIdx == 1) ? scheduleDate : 0;
                        intIdx++;
                    } else if (t == CharSequence.class) {
                        args[i] = caption;
                    } else if (t == ArrayList.class) {
                        args[i] = (listIdx == 0) ? entities : null;
                        listIdx++;
                    } else if (isMessageObjectParam(t, replyToTopMsg)) {
                        if (msgObjIdx == 1) args[i] = replyToTopMsg;
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    m.invoke(null, args);
                    return true;
                } catch (Throwable t) {
                    Logger.e(t);
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean sendDocumentFile(int account, File file, String mime, long peer, Object replyToTopMsg,
                                            String caption, boolean notify) {
        Class<?> smhClass = ClassLoad.getClass(ClassNames.SEND_MESSAGES_HELPER, Utils.classLoader, false);
        Object accInst = getAccountInstance(account);
        if (smhClass == null || accInst == null) return false;

        String methodName = Obfuscate.getMethodName("SendMessagesHelper", "prepareSendingDocument");
        if (methodName == null || "prepareSendingDocument".equals(methodName)) methodName = "prepareSendingDocument";

        for (Method m : smhClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName)
                    && pt.length >= 12 && pt[0].isInstance(accInst)
                    && pt[1] == String.class && pt[2] == String.class && pt[3] == Uri.class
                    && pt[4] == String.class && pt[5] == String.class && pt[6] == long.class) {
                Object[] args = new Object[pt.length];
                args[0] = accInst;
                args[1] = file.getAbsolutePath();
                args[2] = file.getAbsolutePath();
                args[4] = caption;
                args[5] = mime;
                args[6] = peer;
                int boolIdx = 0;
                int msgObjIdx = 0;
                for (int i = 7; i < pt.length; i++) {
                    Class<?> t = pt[i];
                    if (t == boolean.class) {
                        args[i] = (boolIdx == 0) ? notify : Boolean.FALSE;
                        boolIdx++;
                    } else if (t == int.class) {
                        args[i] = 0;
                    } else if (isMessageObjectParam(t, replyToTopMsg)) {
                        if (msgObjIdx == 1) args[i] = replyToTopMsg;
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    m.invoke(null, args);
                    return true;
                } catch (Throwable t) {
                    Logger.e(t);
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean invokePrepareSendingMedia(int account, ArrayList<Object> mediaList, long peer,
                                                     Object replyToTopMsg, boolean forceDocument,
                                                     boolean groupMedia, boolean notify,
                                                     int scheduleDate, int scheduleRepeat) {
        Class<?> smhClass = ClassLoad.getClass(ClassNames.SEND_MESSAGES_HELPER, Utils.classLoader, false);
        Object accInst = getAccountInstance(account);
        if (smhClass == null || accInst == null) return false;

        String methodName = Obfuscate.getMethodName("SendMessagesHelper", "prepareSendingMedia");
        if (methodName == null || "prepareSendingMedia".equals(methodName)) methodName = "prepareSendingMedia";

        for (Method m : smhClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName)
                    && pt.length >= 18 && pt[0].isInstance(accInst)
                    && pt[1] == ArrayList.class && pt[2] == long.class) {
                Object[] args = new Object[pt.length];
                args[0] = accInst;
                args[1] = mediaList;
                args[2] = peer;
                int boolIdx = 0;
                int intIdx = 0;
                int longIdx = 0;
                int msgObjIdx = 0;
                for (int i = 3; i < pt.length; i++) {
                    Class<?> t = pt[i];
                    if (t == long.class) {
                        args[i] = 0L;
                        longIdx++;
                    } else if (t == boolean.class) {
                        if (boolIdx == 0) args[i] = forceDocument;
                        else if (boolIdx == 1) args[i] = groupMedia;
                        else if (boolIdx == 2) args[i] = notify;
                        else args[i] = Boolean.FALSE;
                        boolIdx++;
                    } else if (t == int.class) {
                        args[i] = (intIdx == 0) ? scheduleDate : (intIdx == 1 ? scheduleRepeat : 0);
                        intIdx++;
                    } else if (isMessageObjectParam(t, replyToTopMsg)) {
                        if (msgObjIdx == 1) args[i] = replyToTopMsg;
                        msgObjIdx++;
                    }
                }
                try {
                    m.setAccessible(true);
                    m.invoke(null, args);
                    return true;
                } catch (Throwable t) {
                    Logger.e(t);
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isMessageObjectParam(Class<?> t, Object replyToTopMsg) {
        Class<?> moCls = ClassLoad.getClass(ClassNames.MESSAGE_OBJECT, Utils.classLoader, false);
        if (moCls != null && t == moCls) return true;
        if (t.getName().contains("MessageObject")) return true;
        return replyToTopMsg != null && t.isInstance(replyToTopMsg);
    }

    private static Object getAccountInstance(int account) {
        Class<?> accCls = ClassLoad.getClass(ClassNames.ACCOUNT_INSTANCE, Utils.classLoader, false);
        if (accCls == null) return null;
        String getInst = Obfuscate.getMethodName("AccountInstance", "getInstance");
        if (getInst == null || "getInstance".equals(getInst)) getInst = "getInstance";
        try {
            return ReflectHelper.callStaticMethod(accCls, getInst, account);
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static File resolvePhotoFile(int account, Object owner, Object photo) {
        File attach = checkAttachPath(owner);
        if (attach != null) return attach;

        FileLoader fl = FileLoader.getInstance(account);
        List<Object> sizesDesc = getPhotoSizesDesc(photo);
        for (Object ps : sizesDesc) {
            File f = resolveExistingFile(callGetPathToAttach(fl, ps));
            if (f != null) return f;
        }
        File f = resolveExistingFile(callGetPathToAttach(fl, photo));
        if (f != null) return f;
        return resolveExistingFile(callGetPathToMessage(fl, owner));
    }

    private static File resolveDocumentFile(int account, Object owner, Object document) {
        File attach = checkAttachPath(owner);
        if (attach != null) return attach;

        String localPath = getStringField(document, "localPath");
        if (!TextUtils.isEmpty(localPath)) {
            File localFile = new File(localPath);
            if (existsNonEmpty(localFile)) return localFile;
        }

        FileLoader fl = FileLoader.getInstance(account);
        File f = resolveExistingFile(callGetPathToMessage(fl, owner));
        if (f != null) return f;

        return resolveExistingFile(callGetPathToAttach(fl, document));
    }

    private static File resolveExistingFile(File candidate) {
        if (existsNonEmpty(candidate)) return candidate;
        return findInMediaDirs(candidate);
    }

    private static File checkAttachPath(Object owner) {
        String p = getStringField(owner, "attachPath");
        if (p != null && !p.isEmpty()) {
            File f = new File(p);
            if (existsNonEmpty(f)) return f;
        }
        return null;
    }

    private static List<Object> getPhotoSizesDesc(Object photo) {
        ArrayList<Object> sizes = getListField(photo, "sizes");
        if (sizes == null || sizes.isEmpty()) return Collections.emptyList();
        List<Object> candidates = new ArrayList<>();
        for (Object ps : sizes) {
            if (ps == null) continue;
            String sn = ps.getClass().getSimpleName();
            if (sn.contains("Empty") || sn.contains("Stripped")) continue;
            candidates.add(ps);
        }
        Collections.sort(candidates, new Comparator<Object>() {
            @Override
            public int compare(Object a, Object b) {
                return Long.compare(photoArea(b), photoArea(a));
            }
        });
        return candidates;
    }

    private static long photoArea(Object ps) {
        int w = 0, h = 0;
        try { w = ReflectHelper.getIntField(ps, "w"); } catch (Throwable ignored) {}
        try { h = ReflectHelper.getIntField(ps, "h"); } catch (Throwable ignored) {}
        return (long) w * h;
    }

    private static File callGetPathToMessage(FileLoader fl, Object owner) {
        if (owner == null) return null;
        Class<?> flClass = ClassLoad.getClass(ClassNames.FILE_LOADER, Utils.classLoader, false);
        if (flClass == null) return null;
        String mName = Obfuscate.getMethodName("FileLoader", "getPathToMessage");
        if (mName == null || "getPathToMessage".equals(mName)) mName = "getPathToMessage";
        Object flObj = fl.getFileLoaderObject();
        File firstNonNull = null;
        for (int pass = 0; pass < 2; pass++) {
            boolean wantMapped = (pass == 0);
            boolean matchedMapped = false;
            for (Method m : flClass.getDeclaredMethods()) {
                if (m.getReturnType() != File.class || m.getName().equals(mName) != wantMapped) continue;
                Class<?>[] pt = m.getParameterTypes();
                Object target = Modifier.isStatic(m.getModifiers()) ? null : flObj;
                if (pt.length == 1 && pt[0].isInstance(owner)) {
                    matchedMapped = true;
                    File f = invokeFileMethod(m, target, owner);
                    if (existsNonEmpty(f)) return f;
                    if (firstNonNull == null && f != null) firstNonNull = f;
                } else if (pt.length == 3 && pt[0].isInstance(owner)
                        && pt[1] == boolean.class && pt[2] == boolean.class) {
                    matchedMapped = true;
                    File f = invokeFileMethod(m, target, owner, Boolean.TRUE, Boolean.TRUE);
                    if (existsNonEmpty(f)) return f;
                    if (firstNonNull == null && f != null) firstNonNull = f;
                }
            }
            if (wantMapped && matchedMapped) break;
        }
        return firstNonNull;
    }

    private static File callGetPathToAttach(FileLoader fl, Object tlObj) {
        if (tlObj == null) return null;
        Class<?> flClass = ClassLoad.getClass(ClassNames.FILE_LOADER, Utils.classLoader, false);
        if (flClass == null) return null;
        String mName = Obfuscate.getMethodName("FileLoader", "getPathToAttach");
        if (mName == null || "getPathToAttach".equals(mName)) mName = "getPathToAttach";
        Object flObj = fl.getFileLoaderObject();
        File firstNonNull = null;
        for (int pass = 0; pass < 2; pass++) {
            boolean wantMapped = (pass == 0);
            boolean matchedMapped = false;
            for (Method m : flClass.getDeclaredMethods()) {
                if (m.getReturnType() != File.class || m.getName().equals(mName) != wantMapped) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 1 || !pt[0].isInstance(tlObj)) continue;
                Object target = Modifier.isStatic(m.getModifiers()) ? null : flObj;
                if (pt.length == 1) {
                    matchedMapped = true;
                    File f = invokeFileMethod(m, target, tlObj);
                    if (existsNonEmpty(f)) return f;
                    if (firstNonNull == null && f != null) firstNonNull = f;
                } else if (pt.length == 2 && pt[1] == boolean.class) {
                    matchedMapped = true;
                    for (boolean forceCache : new boolean[]{false, true}) {
                        File f = invokeFileMethod(m, target, tlObj, forceCache);
                        if (existsNonEmpty(f)) return f;
                        if (firstNonNull == null && f != null) firstNonNull = f;
                    }
                } else if (pt.length == 3 && pt[1] == String.class && pt[2] == boolean.class) {
                    matchedMapped = true;
                    for (boolean forceCache : new boolean[]{false, true}) {
                        File f = invokeFileMethod(m, target, tlObj, null, forceCache);
                        if (existsNonEmpty(f)) return f;
                        if (firstNonNull == null && f != null) firstNonNull = f;
                    }
                }
            }
            if (wantMapped && matchedMapped) break;
        }
        return firstNonNull;
    }

    private static File invokeFileMethod(Method m, Object target, Object... args) {
        try {
            m.setAccessible(true);
            Object res = m.invoke(target, args);
            return res instanceof File ? (File) res : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File findInMediaDirs(File orig) {
        if (orig == null) return null;
        String fileName = orig.getName();
        if (TextUtils.isEmpty(fileName)) return null;

        Method dirMethod = cachedGetDirMethod;
        if (dirMethod == null && !getDirMethodScanned) {
            synchronized (ForwardAsCopyHelper.class) {
                if (!getDirMethodScanned) {
                    Class<?> flClass = ClassLoad.getClass(ClassNames.FILE_LOADER, Utils.classLoader, false);
                    if (flClass != null) {
                        String name = Obfuscate.getMethodName("FileLoader", "getDirectory");
                        if (name == null || "getDirectory".equals(name)) name = "getDirectory";
                        Method mapped = ReflectHelper.findMethodExactIfExists(flClass, name, int.class);
                        if (mapped != null && mapped.getReturnType() == File.class) {
                            cachedGetDirMethod = mapped;
                        } else {
                            for (Method m : flClass.getDeclaredMethods()) {
                                Class<?>[] pt = m.getParameterTypes();
                                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == File.class
                                        && pt.length == 1 && pt[0] == int.class) {
                                    cachedGetDirMethod = m;
                                    break;
                                }
                            }
                        }
                    }
                    getDirMethodScanned = true;
                    dirMethod = cachedGetDirMethod;
                }
            }
        }
        if (dirMethod == null) return null;

        for (int dirId : MEDIA_DIRS) {
            File dir = invokeFileMethod(dirMethod, null, dirId);
            if (dir != null) {
                File candidate = new File(dir, fileName);
                if (existsNonEmpty(candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean existsNonEmpty(File f) {
        if (f == null || !f.exists() || !f.isFile() || f.length() <= 0) return false;
        String name = f.getName();
        return !name.endsWith(".temp") && !name.endsWith(".preload");
    }

    private static boolean isEmptyOrWebPageMedia(Object media) {
        if (media == null) return true;
        String sn = media.getClass().getSimpleName();
        return sn.contains("MediaEmpty") || sn.contains("MediaWebPage");
    }

    private static boolean isStickerMessage(Object msgObj, Object document) {
        ArrayList<Object> attrs = getListField(document, "attributes");
        if (attrs != null) {
            for (Object a : attrs) {
                if (a != null && a.getClass().getSimpleName().contains("Sticker")) return true;
            }
        }
        return false;
    }

    private static boolean isVideoDocument(Object document) {
        ArrayList<Object> attrs = getListField(document, "attributes");
        if (attrs != null) {
            for (Object a : attrs) {
                if (a != null && a.getClass().getSimpleName().contains("AttributeVideo")) return true;
            }
        }
        String mime = getStringField(document, "mime_type");
        return mime != null && mime.startsWith("video/");
    }

    private static final class Attribution {
        final String prefix;
        final String plainFallback;
        final ArrayList<Object> entities;

        Attribution(String prefix, String plainFallback, ArrayList<Object> entities) {
            this.prefix = prefix;
            this.plainFallback = plainFallback;
            this.entities = entities;
        }
    }

    private static Attribution buildAttribution(Object msgObj, Object owner, boolean forwardFromMyName) {
        if (forwardFromMyName || ConfigManager.sayWhereCopyCameFrom == null || !ConfigManager.sayWhereCopyCameFrom.isEnable()) {
            return null;
        }
        long did = getMessageDialogId(msgObj, owner);
        if (did == 0L) return null;

        int msgId = getIntField(owner, "id");
        String chatTitle = null;
        String messageUrl = null;
        String senderName = null;
        String senderUrl = null;
        Object senderUser = null;
        Object mc = null;

        try {
            mc = MessagesController.getMessagesController().getMessagesControllerObject();
            if (did < 0) {
                long chatAbsId = -did;
                Object chat = getChatById(mc, chatAbsId);
                if (chat != null) {
                    chatTitle = getStringField(chat, "title");
                }

                String chatUsername = getActiveUsername(chat);
                Object peerId = getField(owner, "peer_id");
                long channelId = peerId != null ? getLongField(peerId, "channel_id") : 0L;
                if (channelId == 0L && chat != null
                        && (getBooleanField(chat, "megagroup") || getBooleanField(chat, "broadcast"))) {
                    channelId = chatAbsId;
                }

                int topicId = 0;
                Object replyTo = getField(owner, "reply_to");
                if (replyTo != null && getBooleanField(replyTo, "forum_topic")) {
                    topicId = getIntField(replyTo, "reply_to_top_id");
                    if (topicId <= 0) {
                        topicId = getIntField(replyTo, "reply_to_msg_id");
                    }
                }

                if (msgId > 0) {
                    if (!TextUtils.isEmpty(chatUsername)) {
                        messageUrl = topicId > 0
                                ? ("https://t.me/" + chatUsername + "/" + topicId + "/" + msgId)
                                : ("https://t.me/" + chatUsername + "/" + msgId);
                    } else if (channelId > 0L) {
                        messageUrl = topicId > 0
                                ? ("https://t.me/c/" + channelId + "/" + topicId + "/" + msgId)
                                : ("https://t.me/c/" + channelId + "/" + msgId);
                    }
                }

                Object fromPeer = getField(owner, "from_id");
                if (fromPeer != null) {
                    long fromUserId = getLongField(fromPeer, "user_id");
                    long fromChannelId = getLongField(fromPeer, "channel_id");
                    if (fromUserId != 0L) {
                        senderUser = getUserById(mc, fromUserId);
                        senderName = formatUserName(senderUser, fromUserId);
                        String su = getActiveUsername(senderUser);
                        if (!TextUtils.isEmpty(su)) {
                            senderUrl = "https://t.me/" + su;
                        }
                    } else if (fromChannelId != 0L && fromChannelId != chatAbsId) {
                        Object sChat = getChatById(mc, fromChannelId);
                        if (sChat != null) {
                            senderName = getStringField(sChat, "title");
                            String scu = getActiveUsername(sChat);
                            if (!TextUtils.isEmpty(scu)) {
                                senderUrl = "https://t.me/" + scu;
                            }
                        }
                    }
                }
                if (TextUtils.isEmpty(senderName)) {
                    String postAuthor = getStringField(owner, "post_author");
                    if (!TextUtils.isEmpty(postAuthor)) {
                        senderName = postAuthor;
                    }
                }
                if (senderName != null && senderName.equals(chatTitle)) {
                    senderName = null;
                    senderUser = null;
                    senderUrl = null;
                }
            } else {
                senderUser = getUserById(mc, did);
                senderName = formatUserName(senderUser, did);
                String su = getActiveUsername(senderUser);
                if (!TextUtils.isEmpty(su)) {
                    senderUrl = "https://t.me/" + su;
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }

        String chatLabel = null;
        if (!TextUtils.isEmpty(chatTitle)) {
            chatLabel = msgId > 0 ? (chatTitle + " (#" + msgId + ")") : chatTitle;
        } else if (messageUrl != null && msgId > 0) {
            chatLabel = "#" + msgId;
        }

        if (TextUtils.isEmpty(senderName) && TextUtils.isEmpty(chatLabel)) {
            return null;
        }

        StringBuilder sb = new StringBuilder("Forwarded from ");
        int senderStart = -1, senderLen = 0;
        int chatStart = -1, chatLen = 0;

        if (!TextUtils.isEmpty(senderName) && chatLabel != null) {
            senderStart = sb.length();
            sb.append(senderName);
            senderLen = senderName.length();
            sb.append(" in ");
            chatStart = sb.length();
            sb.append(chatLabel);
            chatLen = chatLabel.length();
        } else if (chatLabel != null) {
            chatStart = sb.length();
            sb.append(chatLabel);
            chatLen = chatLabel.length();
        } else {
            senderStart = sb.length();
            sb.append(senderName);
            senderLen = senderName.length();
            if (msgId > 0) {
                sb.append(" (#").append(msgId).append(")");
            }
        }

        int headerLineLen = sb.length();
        ArrayList<Object> entities = new ArrayList<>();
        Object italic = createItalicEntity(0, headerLineLen);
        if (italic != null) entities.add(italic);

        if (senderStart >= 0 && senderLen > 0) {
            Object senderEnt = null;
            if (senderUrl != null) {
                senderEnt = createTextUrlEntity(senderStart, senderLen, senderUrl);
            } else if (senderUser != null && mc != null) {
                senderEnt = createMentionNameEntity(mc, senderStart, senderLen, senderUser);
            }
            if (senderEnt != null) entities.add(senderEnt);
        }
        if (chatStart >= 0 && chatLen > 0 && messageUrl != null) {
            Object chatEnt = createTextUrlEntity(chatStart, chatLen, messageUrl);
            if (chatEnt != null) entities.add(chatEnt);
        }

        String headerLine = sb.toString();
        String prefix = headerLine + "\n\n";
        String plainFallback = (messageUrl != null ? (headerLine + "\n" + messageUrl) : headerLine) + "\n\n";
        return new Attribution(prefix, plainFallback, entities);
    }

    private static ArrayList<Object> combineEntities(Attribution attr, ArrayList<Object> rawEntities, boolean includeBodyEntities) {
        ArrayList<Object> result = new ArrayList<>();
        if (attr != null && attr.entities != null) {
            result.addAll(attr.entities);
        }
        if (includeBodyEntities && rawEntities != null && !rawEntities.isEmpty()) {
            int shift = attr != null ? attr.prefix.length() : 0;
            ArrayList<Object> shifted = cloneAndShiftEntities(rawEntities, shift);
            if (shifted != null) {
                result.addAll(shifted);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String getActiveUsername(Object obj) {
        if (obj == null) return null;
        String u = getStringField(obj, "username");
        if (!TextUtils.isEmpty(u)) return u;
        ArrayList<Object> list = getListField(obj, "usernames");
        if (list != null) {
            for (Object item : list) {
                if (item == null) continue;
                if (getBooleanField(item, "active")) {
                    String name = getStringField(item, "username");
                    if (!TextUtils.isEmpty(name)) return name;
                }
            }
        }
        return null;
    }

    private static String formatUserName(Object user, long fallbackId) {
        if (user != null) {
            String fn = getStringField(user, "first_name");
            String ln = getStringField(user, "last_name");
            String full = ((fn != null ? fn : "") + (ln != null && !ln.isEmpty() ? " " + ln : "")).trim();
            if (!full.isEmpty()) return full;
            String uname = getActiveUsername(user);
            if (!TextUtils.isEmpty(uname)) return "@" + uname;
        }
        return fallbackId != 0L ? ("User " + fallbackId) : null;
    }

    private static Object createTextUrlEntity(int offset, int length, String url) {
        try {
            Class<?> cls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$TL_messageEntityTextUrl", Utils.classLoader);
            if (cls == null) return null;
            Object ent = cls.newInstance();
            ReflectHelper.setIntField(ent, "offset", offset);
            ReflectHelper.setIntField(ent, "length", length);
            ReflectHelper.setObjectField(ent, "url", url);
            return ent;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createItalicEntity(int offset, int length) {
        try {
            Class<?> cls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$TL_messageEntityItalic", Utils.classLoader);
            if (cls == null) return null;
            Object ent = cls.newInstance();
            ReflectHelper.setIntField(ent, "offset", offset);
            ReflectHelper.setIntField(ent, "length", length);
            return ent;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createMentionNameEntity(Object mc, int offset, int length, Object user) {
        try {
            Object inputUser = getInputUser(mc, user);
            if (inputUser == null) return null;
            Class<?> cls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$TL_inputMessageEntityMentionName", Utils.classLoader);
            if (cls == null) return null;
            Object ent = cls.newInstance();
            ReflectHelper.setIntField(ent, "offset", offset);
            ReflectHelper.setIntField(ent, "length", length);
            ReflectHelper.setObjectField(ent, "user_id", inputUser);
            return ent;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getInputUser(Object mc, Object user) {
        if (mc == null || user == null) return null;
        Class<?> inputUserCls = ReflectHelper.findClassIfExists("org.telegram.tgnet.TLRPC$InputUser", Utils.classLoader);
        Class<?> userCls = ClassLoad.getClass(ClassNames.TLRPC_USER, Utils.classLoader, false);
        if (inputUserCls != null && userCls != null) {
            for (Method m : mc.getClass().getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == inputUserCls) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 1 && pt[0] == userCls) {
                        try {
                            m.setAccessible(true);
                            return m.invoke(mc, user);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        try {
            return ReflectHelper.callMethod(mc, "getInputUser", user);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String buildCombinedText(String prefix, String body, boolean trimEmptyBody) {
        String b = body != null ? body : "";
        if (prefix == null) return b;
        if (b.isEmpty() && trimEmptyBody) return prefix.trim();
        return prefix + b;
    }

    private static ArrayList<Object> cloneAndShiftEntities(ArrayList<Object> entities, int shift) {
        if (entities == null || entities.isEmpty()) return null;
        if (shift <= 0) return new ArrayList<>(entities);
        ArrayList<Object> out = new ArrayList<>(entities.size());
        for (Object ent : entities) {
            if (ent == null) continue;
            try {
                Object copy = ent.getClass().newInstance();
                for (Field f : ent.getClass().getFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);
                    f.set(copy, f.get(ent));
                }
                int off = ReflectHelper.getIntField(copy, "offset");
                ReflectHelper.setIntField(copy, "offset", off + shift);
                out.add(copy);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static void showToast(String msg) {
        MAIN_HANDLER.post(() -> {
            Activity act = Utils.getCurrentActivity();
            if (act != null) {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static Object getOwner(Object msgObj) {
        if (msgObj == null) return null;
        String ownerFieldName = Obfuscate.getFieldName("MessageObject", "messageOwner");
        if (ownerFieldName != null) {
            try {
                Object val = ReflectHelper.getObjectField(msgObj, ownerFieldName);
                if (val != null) return val;
            } catch (Throwable ignored) {}
        }
        Field fallback = cachedOwnerField;
        if (fallback == null && !ownerFieldScanned) {
            synchronized (ForwardAsCopyHelper.class) {
                if (!ownerFieldScanned) {
                    Class<?> msgCls = ClassLoad.getClass(ClassNames.MESSAGE, Utils.classLoader, false);
                    if (msgCls != null) {
                        for (Class<?> c = msgObj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                            for (Field f : c.getDeclaredFields()) {
                                if (!Modifier.isStatic(f.getModifiers()) && msgCls.isAssignableFrom(f.getType())) {
                                    f.setAccessible(true);
                                    cachedOwnerField = f;
                                    break;
                                }
                            }
                            if (cachedOwnerField != null) break;
                        }
                    }
                    ownerFieldScanned = true;
                    fallback = cachedOwnerField;
                }
            }
        }
        if (fallback != null) {
            try {
                return fallback.get(msgObj);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object getField(Object obj, String field) {
        if (obj == null) return null;
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
        if (obj == null) return 0;
        try {
            return ReflectHelper.getIntField(obj, field);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long getLongField(Object obj, String field) {
        if (obj == null) return 0L;
        try {
            return ReflectHelper.getLongField(obj, field);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long getLongOrIntField(Object obj, String field) {
        if (obj == null) return 0L;
        try {
            Field f = ReflectHelper.findFieldIfExists(obj.getClass(), field);
            if (f != null) {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).longValue();
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private static boolean getBooleanField(Object obj, String field) {
        if (obj == null) return false;
        try {
            return ReflectHelper.getBooleanField(obj, field);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<Object> getListField(Object obj, String field) {
        Object val = getField(obj, field);
        return val instanceof ArrayList ? (ArrayList<Object>) val : null;
    }
}
