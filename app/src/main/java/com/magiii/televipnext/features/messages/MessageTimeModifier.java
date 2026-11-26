package com.magiii.televipnext.features.messages;

import android.content.res.Resources;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;
import com.magiii.televipnext.virtuals.messenger.MessageObject;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;
import com.magiii.televipnext.virtuals.ui.Cells.ChatMessageCell;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.magiii.televipnext.xposed.ReflectHelper;

public class MessageTimeModifier {

    public static boolean loaded;
    private static boolean formatterHooked;
    private static final ThreadLocal<Boolean> IN_FORMATTER_HOOK = new ThreadLocal<>();

    public static boolean isSecondsEnabled() {
        return ConfigManager.showTimeSeconds != null
                && ConfigManager.showTimeSeconds.isEnable();
    }

    public static void init() {
        if (!loaded) {
            loaded = true;
            try {
                HMethod.hookMethod(
                        ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL),
                        Obfuscate.getMethodName("ChatMessageCell", "measureTime"),
                        ArgsResolver.merge("measureTime", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT)},
                                new BaseMethodHook() {
                                    @Override
                                    protected void afterMethod(MethodHookParam param) {
                                        try {
                                            apply(param.thisObject, param.args[0]);
                                        } catch (Throwable e) {
                                            Logger.e(e);
                                        }
                                    }
                                }));
            } catch (Throwable e) {
                Logger.e(e);
            }
        }

        if (!formatterHooked && isSecondsEnabled()) {
            initFormatterHook();
        }
    }

    /**
     * Hooks LocaleController.getFormatterDay() so Telegram renders timestamps with seconds across the app.
     */
    private static void initFormatterHook() {
        if (formatterHooked) return;
        try {
            Class<?> localeCls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER, Utils.classLoader, false);
            if (localeCls == null) return;

            String dayName = resolveLocaleMethod(localeCls, "getFormatterDay", false);
            String daySecName = resolveLocaleMethod(localeCls, "getFormatterDayWithSeconds", true);
            if (dayName == null || daySecName == null || dayName.equals(daySecName)) {
                return;
            }

            final String targetSecMethod = daySecName;
            formatterHooked = true;
            HMethod.hookMethod(localeCls, dayName, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (!isSecondsEnabled() || Boolean.TRUE.equals(IN_FORMATTER_HOOK.get())) {
                        return;
                    }
                    IN_FORMATTER_HOOK.set(Boolean.TRUE);
                    try {
                        Object secFmt = ReflectHelper.callMethod(param.thisObject, targetSecMethod);
                        if (secFmt != null) {
                            param.setResult(secFmt);
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    } finally {
                        IN_FORMATTER_HOOK.remove();
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static String resolveLocaleMethod(Class<?> localeCls, String mappingKey, boolean withSeconds) {
        String mapped = Obfuscate.getMethodName("LocaleController", mappingKey);
        if (mapped != null && ReflectHelper.findMethodExactIfExists(localeCls, mapped) != null) {
            return mapped;
        }
        try {
            String getInstName = Obfuscate.getMethodName("LocaleController", "getInstance");
            Object instance = null;
            if (getInstName != null && ReflectHelper.findMethodExactIfExists(localeCls, getInstName) != null) {
                instance = ReflectHelper.callStaticMethod(localeCls, getInstName);
            }
            if (instance == null) return null;

            for (Method m : localeCls.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                String fastDateFmtName = Obfuscate.getClassName(ClassNames.FAST_DATE_FORMAT);
                if (!fastDateFmtName.equals(m.getReturnType().getName())) continue;
                m.setAccessible(true);
                Object fmt = m.invoke(instance);
                if (fmt == null) continue;
                String pattern = (String) ReflectHelper.callMethod(fmt, "getPattern");
                if (pattern == null || !pattern.contains("mm")) continue;
                if (pattern.indexOf('d') >= 0 || pattern.indexOf('M') >= 0
                        || pattern.indexOf('y') >= 0 || pattern.indexOf('E') >= 0) {
                    continue;
                }
                boolean hasSec = pattern.contains("ss");
                if (hasSec == withSeconds) {
                    return m.getName();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void apply(Object cellObject, Object messageObject)
    {
        boolean id =
                ConfigManager.showMessageId != null &&
                        ConfigManager.showMessageId.isEnable();
        boolean showSeconds = isSecondsEnabled();

        try {
            MessageObject msg = new MessageObject(messageObject);

            TLRPC.Message message = msg.getMessageOwner();

            if (message.get_Message() == null)
                return;

            boolean hasDeletedFlag = (message.getFlags() & ShowDeletedMessages.FLAG_DELETED) != 0;
            if (!id && !hasDeletedFlag && !showSeconds)
                return;

            if (showSeconds) {
                int date = message.getDate();
                if (date > 0 && date < 0x7ffffff0) {
                    appendSecondsToTime(cellObject, ((date % 60) + 60) % 60);
                }
            }

            String prefix = null;
            boolean redColor = false;

            if (id && message.getId() != 0) {
                prefix = "ID " + message.getId();
            }

            if (hasDeletedFlag) {
                prefix = Translator.get(Keys.Deleted);
                redColor = true;
            }

            if (prefix != null)
                prependTime(cellObject, prefix, redColor);

        } catch (Throwable e) {
            Logger.e(e);
        }

    }

    /**
     * Inserts two-digit seconds after the HH:mm portion of the message timestamp and expands time widths.
     */
    private static void appendSecondsToTime(Object object, int seconds) {
        ChatMessageCell cell = new ChatMessageCell(object);
        SpannableStringBuilder time = convertToStringBuilder(cell.getCurrentTimeString());
        if (time == null || time.length() < 4)
            return;

        for (int colonIdx = time.length() - 3; colonIdx >= 1; colonIdx--) {
            if (time.charAt(colonIdx) != ':') continue;
            char base = digitZeroBase(time.charAt(colonIdx - 1));
            if (base == 0) continue;
            if (digitZeroBase(time.charAt(colonIdx + 1)) != base
                    || digitZeroBase(time.charAt(colonIdx + 2)) != base) {
                continue;
            }
            int hourStart = colonIdx - 1;
            if (hourStart > 0 && digitZeroBase(time.charAt(hourStart - 1)) == base) {
                hourStart--;
            }
            if (hourStart > 0 && time.charAt(hourStart - 1) == ':') {
                continue;
            }
            if (colonIdx + 3 < time.length() && time.charAt(colonIdx + 3) == ':') {
                return;
            }
            if (colonIdx + 3 < time.length() && digitZeroBase(time.charAt(colonIdx + 3)) != 0) {
                continue;
            }

            String secStr = new String(new char[]{
                    ':',
                    (char) (base + (seconds / 10)),
                    (char) (base + (seconds % 10))
            });
            time.insert(colonIdx + 3, secStr);
            cell.setCurrentTimeString(time);

            TextPaint paint = Theme.getTextPaint();
            if (paint == null) {
                paint = getFallbackTimePaint(object);
            }
            if (paint != null) {
                int ceil = (int) Math.ceil(paint.measureText(secStr));
                cell.setTimeTextWidth(ceil + cell.getTimeTextWidth());
                cell.setTimeWidth(ceil + cell.getTimeWidth());
            }
            return;
        }
    }

    private static char digitZeroBase(char c) {
        if (c >= '0' && c <= '9') return '0';
        if (c >= '\u0660' && c <= '\u0669') return '\u0660';
        if (c >= '\u06F0' && c <= '\u06F9') return '\u06F0';
        return 0;
    }

    public static SpannableStringBuilder convertToStringBuilder(CharSequence charSequence) {
        if (charSequence != null)
            return charSequence instanceof SpannableStringBuilder ? (SpannableStringBuilder) charSequence : new SpannableStringBuilder(charSequence);
        else
            return null;
    }

    private static void prependTime(Object object, String value, boolean red) {
        ChatMessageCell cell = new ChatMessageCell(object);
        SpannableStringBuilder time = convertToStringBuilder(cell.getCurrentTimeString());

        if (time == null)
            return;

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(value);

        if (red)
            spannableStringBuilder.setSpan(new ForegroundColorSpan(Color.rgb(255, 0, 0)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableStringBuilder.append(" ");

        time.insert(0, spannableStringBuilder);

        cell.setCurrentTimeString(time);
        TextPaint paint = Theme.getTextPaint();
        if (paint == null) {
            paint = getFallbackTimePaint(object);
        }

        if (paint != null) {
            int ceil = (int) Math.ceil(paint.measureText(spannableStringBuilder, 0, spannableStringBuilder.length()));
            cell.setTimeTextWidth(ceil + cell.getTimeTextWidth());
            cell.setTimeWidth(ceil + cell.getTimeWidth());
        }

    }

    private static TextPaint fallbackTimePaint;

    private static TextPaint getFallbackTimePaint(Object cellObject) {
        if (fallbackTimePaint == null) {
            float density = (cellObject instanceof View)
                    ? ((View) cellObject).getResources().getDisplayMetrics().density
                    : Resources.getSystem().getDisplayMetrics().density;
            TextPaint tp = new TextPaint();
            tp.setTextSize(12f * density);
            fallbackTimePaint = tp;
        }
        return fallbackTimePaint;
    }

}