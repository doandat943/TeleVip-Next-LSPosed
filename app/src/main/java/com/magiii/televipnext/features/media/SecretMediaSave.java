package com.magiii.televipnext.features.media;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.MessageObject;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;
import com.magiii.televipnext.virtuals.ui.Cells.ChatMessageCell;
import com.magiii.televipnext.virtuals.ui.PhotoViewer;
import com.magiii.televipnext.virtuals.ui.SecretMediaViewer;

import java.io.File;

public class SecretMediaSave {

    public static boolean isEnable = false;

    public static long id;
    public static File pathImage;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.MESSAGE_OBJECT) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "isSecret"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.secretMediaSave.isEnable()) param.setResult(false);
                        }
                    });
                }
                if (ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE), Obfuscate.getMethodName("ChatActivity$ChatMessageCellDelegate", "didPressImage"), ArgsResolver.merge("didPressImage", new Class[]{ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL), float.class, float.class, boolean.class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                boolean saveOn = ConfigManager.secretMediaSave != null && ConfigManager.secretMediaSave.isEnable();
                                boolean preventOn = ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable();
                                if ((saveOn || preventOn) && param.args[0] != null) {
                                    ChatMessageCell messageCell = new ChatMessageCell(param.args[0]);
                                    if (messageCell.getChatMessageCell() != null) {
                                        MessageObject messageObject = messageCell.getMessageObject();
                                        if (messageObject.getMessageObject() != null) {
                                            TLRPC.Message message = messageObject.getMessageOwner();
                                            if (saveOn && message.getTtl() > 0) {
                                                message.setTtl(0);
                                            } else if (preventOn && message.getTtl() == 0x7FFFFFFF) {
                                                message.setTtl(0);
                                            }
                                        }
                                        if (saveOn) {
                                            bindPhotoViewerToActivity(messageCell);
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    }));
                }

                if (ClassLoad.getClass(ClassNames.FILE_LOADER) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getPathToMessage"), ArgsResolver.merge("getPathToMessage", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null && pathImage != null) {
                                    TLRPC.Message message = new TLRPC.Message(param.args[0]);
                                    if (message.getId() == id) {
                                        param.setResult(pathImage);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    }));
                }


                if (ConfigManager.secretMediaSave.isEnable()) SecretMediaViewer.openMedia();
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

    private static void bindPhotoViewerToActivity(ChatMessageCell cell) {
        try {
            if (!(cell.getChatMessageCell() instanceof View)) return;
            View view = (View) cell.getChatMessageCell();
            Context context = view.getContext();
            Activity activity = extractActivityFromContext(context);
            if (activity == null) return;

            PhotoViewer.getInstance().setParentActivity(activity);
        } catch (Throwable ignored) {}
    }

    private static Activity extractActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
