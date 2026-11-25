package com.my.televip.virtuals.ui;

import android.app.Activity;

import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.features.media.SecretMediaSave;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.messenger.FileLoader;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.tgnet.TLRPC;

import java.io.File;

import de.robv.android.xposed.XposedBridge;

public class SecretMediaViewer {

    private static boolean isEnable = false;

    public static void openMedia() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> secretViewerClass = ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER);
                if (secretViewerClass == null) return;

                String openMediaMethod = Obfuscate.getMethodName("SecretMediaViewer", "openMedia");
                XposedBridge.hookAllMethods(secretViewerClass, openMediaMethod, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (param.args.length < 2 || param.args[0] == null) return;

                        boolean secretMediaSave = ConfigManager.secretMediaSave != null && ConfigManager.secretMediaSave.isEnable();
                        boolean preventMedia = ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable();
                        if (preventMedia && !secretMediaSave) {
                            if (param.args.length >= 3) param.args[2] = null;
                            if (param.args.length >= 4) param.args[3] = null;

                            MessageObject messageObject = new MessageObject(param.args[0]);
                            if (messageObject.getMessageObject() != null) {
                                TLRPC.Message messageOwner = messageObject.getMessageOwner();
                                if (messageOwner.get_Message() != null) {
                                    messageOwner.setTtl(0);
                                }
                            }
                        }

                        if (secretMediaSave && param.args[1] != null) {
                            MessageObject messageObject = new MessageObject(param.args[0]);
                            TLRPC.Message messageOwner = messageObject.getMessageOwner();
                            if (messageOwner != null && messageOwner.get_Message() != null && messageOwner.getTtl() > 0) {
                                messageOwner.setTtl(0);
                            }

                            PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.PhotoViewerProvider(param.args[1]);
                            try {
                                final PhotoViewer.PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, null, 0, true, false);
                                if (object != null && object.getImageReceiver().imageReceiver != null) {
                                    FileLoader fileLoader = FileLoader.getInstance(UserConfig.getSelectedAccount());
                                    File image = fileLoader.getLocalFile(object.getImageReceiver().getImageLocation());
                                    if (image != null) {
                                        SecretMediaSave.pathImage = image;
                                        SecretMediaSave.id = messageObject.getMessageOwner().getId();
                                    }
                                }
                            } catch (Throwable ignored) {}

                            PhotoViewer pv = PhotoViewer.getInstance();
                            Activity act = Utils.getCurrentActivity();
                            if (act != null) {
                                try {
                                    pv.setParentActivity(act);
                                } catch (Throwable ignored) {}
                            }
                            pv.openPhoto(messageObject, messageObject.getDialogId(), 0L, 0L, provider, false);
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

}