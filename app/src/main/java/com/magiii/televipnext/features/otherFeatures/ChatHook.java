package com.magiii.televipnext.features.otherFeatures;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.settings.ui.GlassDialog;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.ActionBarMenuItem;
import com.magiii.televipnext.virtuals.ui.ChatActivity;

import com.magiii.televipnext.xposed.ReflectHelper;
import com.magiii.televipnext.xposed.XposedCompat;

public class ChatHook {

    private static boolean initialized = false;

    public static void init(String className) {
        if (initialized || ClientManager.is(ClientManager.Client.Nagram) || ClientManager.is(ClientManager.Client.TelegramPlus)) return;

        Class<?> clazz = ClassLoad.getClass(className);
        if (clazz == null) FeatureStateManager.reset();
        try {
            initialized = true;
            HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "createView"), ArgsResolver.merge("createView", new Class[]{Context.class}, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    try {
                        ChatActivity chatActivity = new ChatActivity(param.thisObject);

                        ActionBarMenuItem headerItem = chatActivity.getHeaderItem();
                        if (headerItem.getActionBarMenuItem() != null) {

                            int drawableResource = ReflectHelper.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "msg_go_up");

                            if (!ClientManager.is(ClientManager.Client.iMe) && !ClientManager.is(ClientManager.Client.iMeWeb) && !ClientManager.is(ClientManager.Client.TelegramPlus) && !ClientManager.is(ClientManager.Client.XPlus) && !ClientManager.is(ClientManager.Client.forkgram) && !ClientManager.is(ClientManager.Client.forkgramBeta)) {
                                headerItem.lazilyAddSubItem(8353847, drawableResource, Translator.get(Keys.ToTheBeginning));
                            }
                            drawableResource = ReflectHelper.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "player_new_order");

                            headerItem.lazilyAddSubItem(8353848, drawableResource, Translator.get(Keys.ToTheMessage));

                        }
                    } catch (Throwable t){
                        Logger.e(t);
                    }

                }
            }));

            XposedCompat.findAndHookMethod(clazz, "onItemClick", int.class, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    try {
                        int id = (int) param.args[0];

                        final Object thisClass = ReflectHelper.getObjectField(param.thisObject, Obfuscate.getFieldName("ChatActivity", "this$0"));
                        ChatActivity chat = new ChatActivity(thisClass);

                        if (id == 8353847) {
                            chat.scrollToMessageId(1, 0, true, 0, true, 0);
                        } else if (id == 8353848) {
                            Activity activity = Utils.getCurrentActivity();
                            if (activity == null) return;

                            EditText input = GlassDialog.createGlassEditText(
                                    activity,
                                    Translator.get(Keys.InputMessageId),
                                    InputType.TYPE_CLASS_NUMBER);

                            new GlassDialog.Builder(activity)
                                    .setTitle(Translator.get(Keys.InputMessageId))
                                    .setView(input)
                                    .setPositiveButton(Translator.get(Keys.Done), () -> {
                                        String text = input.getText().toString().trim();
                                        if (!text.isEmpty()) {
                                            try {
                                                int msgId = Integer.parseInt(text);
                                                chat.scrollToMessageId(msgId, 0, true, 0, true, 0);
                                            } catch (NumberFormatException ignored) {}
                                        }
                                    })
                                    .setNegativeButton(Translator.get(Keys.Cancel), null)
                                    .show();
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
        } catch (Throwable t){
            FeatureStateManager.reset();
        }
    }
}