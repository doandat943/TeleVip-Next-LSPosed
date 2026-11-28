package com.magiii.televipnext.features.otherFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.IdDateEstimator;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.messenger.MessagesController;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nagram XF-style "Show ID and DC in Profile" feature:
 * injects (or replaces partial client-native ID rows with) a TextDetailCell in ProfileActivity's
 * Info Card displaying the plain numeric ID, Data Center (DC), and a blue calendar icon with a
 * floating tooltip for estimated user creation date or channel/group creator/join/creation date,
 * while suppressing redundant partial inline ID/DC views on clients like Nekogram, Nagram, and Telegraph.
 */
public class ShowProfileIdDc {

    private static final int VIEW_TYPE_TEXT_DETAIL = 2;
    private static final int VIEW_TYPE_ABOUT_LINK = 3;
    private static final int VIEW_TYPE_SHADOW = 7;
    private static final int VIEW_TYPE_VERSION = 14;
    private static final int VIEW_TYPE_TEXT_DETAIL_MULTILINE = 19;
    private static final int VIEW_TYPE_SHADOW_TEXT = 26;
    private static final int VIEW_TYPE_TEXT_DETAIL_MULTILINE_2 = 30;

    private static final int TAG_ID_DC_BOUND = 0x54564950;
    private static final int TAG_ORIGINAL_BG = 0x54564951;

    private static volatile boolean initialized = false;

    private static final WeakHashMap<Object, Integer> ID_DC_ROWS = new WeakHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method[]> CHAR_SEQ_ACCESSOR_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IN_SCAN = new ThreadLocal<>();
    private static WeakReference<PopupWindow> activeHintPopup = new WeakReference<>(null);

    private static Class<?> profileClass;
    private static Class<?> listAdapterClass;
    private static Class<?> textDetailCellClass;

    private static Field rowCountField;
    private static Field listAdapterField;
    private static Field nativeIdRowField;
    private static Field[] rowFields;
    private static Field[] outerThisFields;
    private static volatile Field cachedViewHolderItemViewField;
    private static volatile Method cachedGetMessagesControllerMethod;
    private static boolean getMessagesControllerSearched = false;
    private static volatile Method cachedGetConnectionsManagerMethod;
    private static boolean getConnectionsManagerSearched = false;
    private static volatile Method cachedGetUserConfigMethod;
    private static boolean getUserConfigSearched = false;
    private static volatile Method cachedGetCurrentDatacenterIdMethod;
    private static boolean getCurrentDatacenterIdSearched = false;
    private static volatile Method cachedGetClientUserIdMethod;
    private static boolean getClientUserIdSearched = false;

    private static Method getItemViewTypeMethod;
    private static Method setTextAndValueMethod;
    private static Method setImageMethod;
    private static boolean setImageTakesDesc;
    private static Method setImageClickListenerMethod;
    private static Field textDetailImageViewField;

    public static void init() {
        if (initialized) return;
        try {
            profileClass = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
            listAdapterClass = ClassLoad.getClass(ClassNames.PROFILE_LIST_ADAPTER);
            textDetailCellClass = ClassLoad.getClass(ClassNames.TEXT_DETAIL_CELL);
            if (profileClass == null || listAdapterClass == null || textDetailCellClass == null) {
                return;
            }

            String updRowsName = Obfuscate.getMethodName("ProfileActivity", "updateRowsIds");
            String rowCountName = Obfuscate.getFieldName("ProfileActivity", "rowCount");
            String listAdapterFieldName = Obfuscate.getFieldName("ProfileActivity", "listAdapter");
            String rowFieldsStr = Obfuscate.getFieldName("ProfileActivity", "rowFields");
            String outerThisStr = Obfuscate.getFieldName("ProfileActivity$ListAdapter", "this$0");

            String getTypeName = Obfuscate.getMethodName("ProfileActivity$ListAdapter", "getItemViewType");
            String onBindName = Obfuscate.getMethodName("ProfileActivity$ListAdapter", "onBindViewHolder");
            String isEnabledName = Obfuscate.getMethodName("ProfileActivity$ListAdapter", "isEnabled");

            String setTvName = Obfuscate.getMethodName("TextDetailCell", "setTextAndValue");
            String setImg1Name = Obfuscate.getMethodName("TextDetailCell", "setImage1");
            String setImg2Name = Obfuscate.getMethodName("TextDetailCell", "setImage2");
            String setImgClkName = Obfuscate.getMethodName("TextDetailCell", "setImageClickListener");
            String imgFieldName = Obfuscate.getFieldName("TextDetailCell", "imageView");

            rowCountField = ReflectHelper.findFieldIfExists(profileClass, rowCountName);
            listAdapterField = ReflectHelper.findFieldIfExists(profileClass, listAdapterFieldName);
            Method updRowsMethod = ReflectHelper.findMethodExactIfExists(profileClass, updRowsName);

            getItemViewTypeMethod = ReflectHelper.findMethodExactIfExists(listAdapterClass, getTypeName, int.class);
            setTextAndValueMethod = ReflectHelper.findMethodExactIfExists(
                    textDetailCellClass, setTvName, CharSequence.class, CharSequence.class, boolean.class);
            setImageClickListenerMethod = ReflectHelper.findMethodExactIfExists(
                    textDetailCellClass, setImgClkName, View.OnClickListener.class);
            textDetailImageViewField = ReflectHelper.findFieldIfExists(textDetailCellClass, imgFieldName);
            if (textDetailImageViewField == null) {
                for (Field cf : textDetailCellClass.getDeclaredFields()) {
                    if (cf.getType() == ImageView.class) {
                        textDetailImageViewField = cf;
                        break;
                    }
                }
            }

            if (setImg2Name != null && !"setImage2".equals(setImg2Name)) {
                setImageMethod = ReflectHelper.findMethodExactIfExists(
                        textDetailCellClass, setImg2Name, Drawable.class, CharSequence.class);
                if (setImageMethod == null) {
                    setImageMethod = ReflectHelper.findMethodExactIfExists(
                            textDetailCellClass, setImg2Name, Drawable.class, String.class);
                }
                if (setImageMethod != null) {
                    setImageTakesDesc = true;
                }
            }
            if (setImageMethod == null && setImg1Name != null) {
                String name = "setImage1".equals(setImg1Name) ? "setImage" : setImg1Name;
                setImageMethod = ReflectHelper.findMethodExactIfExists(textDetailCellClass, name, Drawable.class);
                setImageTakesDesc = false;
            }
            if (setImageMethod == null) {
                setImageMethod = ReflectHelper.findMethodExactIfExists(
                        textDetailCellClass, "setImage", Drawable.class, CharSequence.class);
                if (setImageMethod != null) {
                    setImageTakesDesc = true;
                } else {
                    setImageMethod = ReflectHelper.findMethodExactIfExists(
                            textDetailCellClass, "setImage", Drawable.class);
                    setImageTakesDesc = false;
                }
            }

            rowFields = resolveRowFields(profileClass, rowFieldsStr);
            outerThisFields = resolveFieldsByCsv(listAdapterClass, outerThisStr);
            String nativeIdRowName = Obfuscate.getFieldName("ProfileActivity", "nativeIdRow");
            if (nativeIdRowName != null && !"nativeIdRow".equals(nativeIdRowName)) {
                Field f = ReflectHelper.findFieldIfExists(profileClass, nativeIdRowName);
                if (f != null && f.getType() == int.class) {
                    f.setAccessible(true);
                    nativeIdRowField = f;
                }
            }

            Method onBindMethod = findMethodByNameAndSuffixParam(listAdapterClass, onBindName, int.class);
            Method isEnabledMethod = findSingleRefParamMethod(listAdapterClass, isEnabledName, boolean.class);

            if (updRowsMethod == null || rowCountField == null || listAdapterField == null
                    || rowFields.length == 0 || outerThisFields.length == 0
                    || getItemViewTypeMethod == null || onBindMethod == null
                    || setTextAndValueMethod == null) {
                Logger.w("ShowProfileIdDc: missing required mapping; aborting init");
                return;
            }

            rowCountField.setAccessible(true);
            listAdapterField.setAccessible(true);
            getItemViewTypeMethod.setAccessible(true);
            setTextAndValueMethod.setAccessible(true);
            if (setImageMethod != null) setImageMethod.setAccessible(true);
            if (setImageClickListenerMethod != null) setImageClickListenerMethod.setAccessible(true);
            if (textDetailImageViewField != null) textDetailImageViewField.setAccessible(true);

            initialized = true;

            HMethod.hookMethod(updRowsMethod, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    onAfterUpdateRowsIds(param.thisObject);
                }
            });

            HMethod.hookMethod(getItemViewTypeMethod, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(IN_SCAN.get()) || !isFeatureEnabled()) return;
                    Object profileObj = resolveProfileObj(param.thisObject);
                    Integer idRow = getIdDcRow(profileObj);
                    if (idRow != null && param.args[0] instanceof Integer && ((Integer) param.args[0]) == idRow.intValue()) {
                        param.setResult(VIEW_TYPE_TEXT_DETAIL);
                    }
                }
            });

            if (isEnabledMethod != null) {
                HMethod.hookMethod(isEnabledMethod, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (!isFeatureEnabled()) return;
                        Object profileObj = resolveProfileObj(param.thisObject);
                        Integer idRow = getIdDcRow(profileObj);
                        if (idRow == null) return;
                        View itemView = extractHolderItemView(param.args[0]);
                        if (itemView != null && Boolean.TRUE.equals(itemView.getTag(TAG_ID_DC_BOUND))
                                && itemView.getTag() instanceof Integer
                                && ((Integer) itemView.getTag()) == idRow.intValue()) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });
            }

            HMethod.hookMethod(onBindMethod, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    Object profileObj = resolveProfileObj(param.thisObject);
                    if (profileObj == null) return;

                    View itemView = extractHolderItemView(param.args[0]);
                    int pos = (int) param.args[1];
                    Integer idRow = isFeatureEnabled() ? getIdDcRow(profileObj) : null;

                    if (idRow == null || pos != idRow) {
                        cleanupRecycledCell(itemView);
                        return;
                    }
                    if (itemView == null || !textDetailCellClass.isInstance(itemView)) {
                        return;
                    }

                    bindIdDcCell(profileObj, itemView, pos);
                    param.setResult(null);
                }
            });

            hookNekogramNativeIdView();
            hookNagramNativeIdText();
            hookTelegraphNativeDcView();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void hookNagramNativeIdText() {
        try {
            String updName = Obfuscate.getMethodName("ProfileActivity", "nagramIdUpdate");
            String viewsCsv = Obfuscate.getFieldName("ProfileActivity", "nagramIdViews");
            if (updName == null || "nagramIdUpdate".equals(updName)
                    || viewsCsv == null || "nagramIdViews".equals(viewsCsv)) {
                return;
            }
            Method updMethod = ReflectHelper.findMethodExactIfExists(profileClass, updName, boolean.class);
            final Field[] viewFields = resolveFieldsByCsv(profileClass, viewsCsv);
            if (updMethod == null || viewFields.length == 0) return;

            HMethod.hookMethod(updMethod, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (!isFeatureEnabled() || param.thisObject == null) return;
                    for (Field f : viewFields) {
                        try {
                            Object val = f.get(param.thisObject);
                            if (val instanceof View) {
                                sanitizeInlineIdView((View) val, false);
                            } else if (val instanceof View[]) {
                                for (View v : (View[]) val) {
                                    sanitizeInlineIdView(v, false);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void hookTelegraphNativeDcView() {
        try {
            String updName = Obfuscate.getMethodName("ProfileActivity", "telegraphDcUpdate");
            String viewFieldName = Obfuscate.getFieldName("ProfileActivity", "telegraphDcView");
            if (updName == null || "telegraphDcUpdate".equals(updName)
                    || viewFieldName == null || "telegraphDcView".equals(viewFieldName)) {
                return;
            }
            Method updMethod = ReflectHelper.findMethodExactIfExists(profileClass, updName);
            final Field dcViewField = ReflectHelper.findFieldIfExists(profileClass, viewFieldName);
            if (updMethod == null || dcViewField == null) return;
            dcViewField.setAccessible(true);

            HMethod.hookMethod(updMethod, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (!isFeatureEnabled() || param.thisObject == null) return;
                    try {
                        Object val = dcViewField.get(param.thisObject);
                        if (val instanceof View) {
                            sanitizeInlineIdView((View) val, true);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void sanitizeInlineIdView(View view, boolean dcPrefixOnly) {
        if (view == null) return;
        try {
            Method[] accessors = findCharSeqAccessors(view.getClass());
            Method getter = accessors[0];
            Method setter = accessors[1];
            if (getter == null || setter == null) return;

            Object curObj = getter.invoke(view);
            if (!(curObj instanceof CharSequence)) return;
            String cur = curObj.toString();

            if (dcPrefixOnly) {
                if (cur.trim().startsWith("DC: ")) {
                    setter.invoke(view, "");
                    view.setVisibility(View.GONE);
                }
                return;
            }

            String cleaned = stripNativeIdDcText(cur);
            if (cleaned != null) {
                setter.invoke(view, cleaned);
                if (cleaned.isEmpty()) {
                    view.setVisibility(View.GONE);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static Method[] findCharSeqAccessors(Class<?> cls) {
        return CHAR_SEQ_ACCESSOR_CACHE.computeIfAbsent(cls, c -> {
            Method getter = null;
            Method setter = null;
            if (TextView.class.isAssignableFrom(c)) {
                try {
                    getter = TextView.class.getMethod("getText");
                    setter = TextView.class.getMethod("setText", CharSequence.class);
                } catch (NoSuchMethodException ignored) {
                }
                return new Method[]{getter, setter};
            }
            for (Class<?> cur = c; cur != null && cur != View.class && cur != Object.class; cur = cur.getSuperclass()) {
                for (Method m : cur.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(mod) || m.isBridge() || m.isSynthetic()) continue;
                    Class<?>[] params = m.getParameterTypes();
                    Class<?> ret = m.getReturnType();
                    if (params.length == 0 && ret == CharSequence.class) {
                        if (getter == null || "getText".equals(m.getName())) {
                            getter = m;
                        }
                    } else if (params.length == 1 && params[0] == CharSequence.class
                            && (ret == boolean.class || ret == void.class)) {
                        if (setter == null
                                || "setText".equals(m.getName())
                                || (!"setText".equals(setter.getName()) && ret == boolean.class && setter.getReturnType() != boolean.class)) {
                            setter = m;
                        }
                    }
                }
            }
            if (getter != null) getter.setAccessible(true);
            if (setter != null) setter.setAccessible(true);
            return new Method[]{getter, setter};
        });
    }

    /**
     * Strips inline client-native "ID: ..." / ", DC: ..." text from profile header subtitle views.
     * Returns "" to hide the view completely, a cleaned string if only a suffix should be stripped,
     * or null if the text does not contain native ID/DC info and should be left untouched.
     */
    public static String stripNativeIdDcText(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.startsWith("ID: ")) {
            return "";
        }
        String[] seps = {" · ID: ", ", ID: ", " | ID: ", "\nID: ", " - ID: "};
        for (String sep : seps) {
            int idx = text.indexOf(sep);
            if (idx >= 0) {
                return text.substring(0, idx).trim();
            }
        }
        return null;
    }

    private static void hookNekogramNativeIdView() {
        try {
            String mappedClass = Obfuscate.getClassName(ClassNames.NEKO_PROFILE_ID_VIEW);
            if (mappedClass == null || ClassNames.NEKO_PROFILE_ID_VIEW.equals(mappedClass)) return;
            Class<?> nekoIdCls = ClassLoad.getClass(ClassNames.NEKO_PROFILE_ID_VIEW);
            String setTextName = Obfuscate.getMethodName("NekoProfileIdView", "setText");
            if (nekoIdCls == null || setTextName == null) return;
            Method setTextM = ReflectHelper.findMethodExactIfExists(nekoIdCls, setTextName, String.class, boolean.class);
            if (setTextM == null) return;
            HMethod.hookMethod(setTextM, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (!isFeatureEnabled()) return;
                    if (param.args[0] instanceof String && ((String) param.args[0]).startsWith("ID: ")) {
                        param.args[0] = "";
                        param.setObjectExtra("tv_hide_neko_id", Boolean.TRUE);
                    }
                }

                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.getObjectExtra("tv_hide_neko_id"))
                            && param.thisObject instanceof View) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static boolean isFeatureEnabled() {
        return ConfigManager.showUserID != null && ConfigManager.showUserID.isEnable();
    }

    private static void onAfterUpdateRowsIds(Object profileObj) {
        if (profileObj == null) return;
        setIdDcRow(profileObj, null);
        if (!isFeatureEnabled()) return;

        try {
            ProfileActivity pa = new ProfileActivity(profileObj);
            long userId = pa.getUserId();
            long chatId = pa.getChatId();
            if (userId == 0 && chatId == 0) return;

            Object adapter = listAdapterField.get(profileObj);
            int rc = rowCountField.getInt(profileObj);
            if (adapter == null || rc <= 0) return;

            int infoSectionPos = -1;
            int usernamePos = -1;
            int lastInfoPos = -1;
            int firstShadowAfterInfo = -1;

            IN_SCAN.set(Boolean.TRUE);
            try {
                for (int i = 0; i < rc; i++) {
                    int vt = (int) getItemViewTypeMethod.invoke(adapter, i);
                    if (vt == VIEW_TYPE_VERSION) {
                        // Settings screen opened via ProfileActivity: never inject ID/DC here.
                        return;
                    }
                    if (infoSectionPos < 0) {
                        if (vt == VIEW_TYPE_SHADOW_TEXT) {
                            infoSectionPos = i;
                        } else {
                            if (vt == VIEW_TYPE_TEXT_DETAIL_MULTILINE && usernamePos < 0) {
                                usernamePos = i;
                            }
                            if (vt == VIEW_TYPE_TEXT_DETAIL
                                    || vt == VIEW_TYPE_ABOUT_LINK
                                    || vt == VIEW_TYPE_TEXT_DETAIL_MULTILINE
                                    || vt == VIEW_TYPE_TEXT_DETAIL_MULTILINE_2) {
                                lastInfoPos = i;
                            } else if (vt == VIEW_TYPE_SHADOW && lastInfoPos >= 0 && firstShadowAfterInfo < 0) {
                                firstShadowAfterInfo = i;
                            }
                        }
                    }
                }
                if (infoSectionPos < 0) {
                    infoSectionPos = firstShadowAfterInfo;
                }
                if (nativeIdRowField != null) {
                    int nativeRow = nativeIdRowField.getInt(profileObj);
                    if (nativeRow >= 0 && nativeRow < rc) {
                        nativeIdRowField.setInt(profileObj, -1);
                        setIdDcRow(profileObj, nativeRow);
                        return;
                    }
                }
                if (infoSectionPos < 0) return;
            } finally {
                IN_SCAN.remove();
            }

            int insertPos;
            if (usernamePos >= 0) {
                insertPos = usernamePos + 1;
            } else if (lastInfoPos >= 0) {
                insertPos = lastInfoPos + 1;
            } else {
                insertPos = infoSectionPos;
            }

            for (Field f : rowFields) {
                int val = f.getInt(profileObj);
                if (val >= insertPos) {
                    f.setInt(profileObj, val + 1);
                }
            }
            rowCountField.setInt(profileObj, rc + 1);
            setIdDcRow(profileObj, insertPos);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void bindIdDcCell(Object profileObj, View itemView, int pos) {
        try {
            ProfileActivity pa = new ProfileActivity(profileObj);
            final long userId = pa.getUserId();
            final long chatId = pa.getChatId();

            MessagesController mc = resolveMessagesController(profileObj);
            Object user = userId != 0 && mc != null ? mc.getUser(userId) : null;
            Object chat = userId == 0 && chatId != 0 && mc != null ? mc.getChat(chatId) : null;
            boolean basicGroup = userId == 0 && isBasicLegacyChat(chat);

            String titleText = formatTitleId(userId, chatId);
            final String rawCopyId = formatRawCopyId(userId, chatId, basicGroup);
            int dcId = 0;
            try {
                dcId = resolveEntityDcId(profileObj, user, chat, userId);
            } catch (Throwable ignored) {
            }
            final String dcLine = formatDcLine(dcId);

            setTextAndValueMethod.invoke(itemView, titleText, dcLine, false);

            if (setImageMethod != null) {
                final long chatJoinDateSec = userId == 0 && chat != null ? readMappedInt(chat, "TLRPC$Chat", "date") : 0;
                final String chatTitle = userId == 0 && chat != null ? readMappedString(chat, "TLRPC$Chat", "title") : null;
                final boolean chatIsCreator = userId == 0 && chat != null && readMappedBoolean(chat, "TLRPC$Chat", "creator");
                final boolean chatIsLeft = userId == 0 && chat != null && readMappedBoolean(chat, "TLRPC$Chat", "left");
                boolean showCalendar = userId > 1 || (chatId != 0 && chatJoinDateSec > 0);
                if (showCalendar) {
                    int iconColor = resolveCalendarAccentColor(itemView);
                    Drawable calDrawable = new CalendarIconDrawable(itemView.getContext(), iconColor);
                    if (setImageTakesDesc) {
                        setImageMethod.invoke(itemView, calDrawable, Translator.get(Keys.ApproximateCreationDate));
                    } else {
                        setImageMethod.invoke(itemView, calDrawable);
                    }
                    final boolean isSelf = userId > 0 && userId == resolveClientUserId(profileObj);
                    applyImageClickListener(itemView, v -> {
                        CharSequence hint = userId > 1
                                ? buildUserCreationHint(userId, isSelf)
                                : buildChatDateHint(chatTitle, chatJoinDateSec, chatIsCreator, chatIsLeft);
                        showFloatingHint(v, hint);
                    });
                } else {
                    if (setImageTakesDesc) {
                        setImageMethod.invoke(itemView, null, null);
                    } else {
                        setImageMethod.invoke(itemView, (Object) null);
                    }
                    applyImageClickListener(itemView, null);
                }
            }

            itemView.setTag(pos);
            itemView.setTag(TAG_ID_DC_BOUND, Boolean.TRUE);
            applySelectableBackground(itemView);

            itemView.setOnClickListener(v -> copyWithFeedback(v, rawCopyId, false));
            itemView.setOnLongClickListener(v -> {
                String longVal = userId > 0
                        ? "tg://openmessage?user_id=" + userId
                        : ("ID: " + rawCopyId + " | " + dcLine);
                copyWithFeedback(v, longVal, true);
                return true;
            });
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void applyImageClickListener(View itemView, View.OnClickListener listener) throws Exception {
        if (setImageClickListenerMethod != null) {
            setImageClickListenerMethod.invoke(itemView, listener);
            return;
        }
        if (textDetailImageViewField != null) {
            ImageView iv = (ImageView) textDetailImageViewField.get(itemView);
            if (iv != null) {
                iv.setOnClickListener(listener);
                if (listener == null) {
                    iv.setClickable(false);
                }
            }
        }
    }

    private static void cleanupRecycledCell(View itemView) {
        if (itemView == null || !Boolean.TRUE.equals(itemView.getTag(TAG_ID_DC_BOUND))) return;
        itemView.setTag(TAG_ID_DC_BOUND, null);
        itemView.setOnClickListener(null);
        itemView.setOnLongClickListener(null);
        itemView.setClickable(false);
        itemView.setLongClickable(false);
        try {
            applyImageClickListener(itemView, null);
        } catch (Throwable ignored) {
        }
        Object origBg = itemView.getTag(TAG_ORIGINAL_BG);
        if (origBg instanceof Drawable) {
            itemView.setBackground((Drawable) origBg);
        } else {
            itemView.setBackground(null);
        }
        itemView.setTag(TAG_ORIGINAL_BG, null);
    }

    private static void applySelectableBackground(View view) {
        if (view.getTag(TAG_ORIGINAL_BG) == null) {
            Drawable bg = view.getBackground();
            view.setTag(TAG_ORIGINAL_BG, bg != null ? bg : Boolean.FALSE);
        }
        try {
            TypedArray ta = view.getContext().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            Drawable ripple = ta.getDrawable(0);
            ta.recycle();
            if (ripple != null) {
                view.setBackground(ripple);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyWithFeedback(View view, String value, boolean longPress) {
        if (value == null || value.isEmpty()) return;
        Context act = Utils.getCurrentActivity();
        Context ctx = act != null ? act : view.getContext();
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("clipboard", value));
        }
        view.performHapticFeedback(longPress ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private static int resolveEntityDcId(Object profileObj, Object user, Object chat, long userId) {
        if (userId != 0) {
            int dc = user != null ? readPhotoDcId(user, "TLRPC$User", "TLRPC$UserProfilePhoto") : 0;
            if (dc > 0) return dc;
            dc = readFullPhotoDcId(profileObj, "userInfo", "profile_photo");
            if (dc > 0) return dc;
            dc = readFullPhotoDcId(profileObj, "userInfo", "fallback_photo");
            if (dc > 0) return dc;
            if (userId > 0 && userId == resolveClientUserId(profileObj)) {
                return resolveCurrentDatacenterId(profileObj);
            }
            return 0;
        }
        if (chat != null) {
            int dc = readPhotoDcId(chat, "TLRPC$Chat", "TLRPC$ChatPhoto");
            if (dc > 0) return dc;
            return readFullPhotoDcId(profileObj, "chatInfo", "chat_photo");
        }
        return 0;
    }

    private static long resolveClientUserId(Object profileObj) {
        try {
            Method getUc = cachedGetUserConfigMethod;
            if (getUc == null && !getUserConfigSearched && profileObj != null) {
                getUserConfigSearched = true;
                Class<?> ucClass = ClassLoad.getClass(ClassNames.USER_CONFIG);
                if (ucClass != null) {
                    getUc = findZeroArgMethodReturning(profileObj.getClass(), ucClass);
                    if (getUc != null) cachedGetUserConfigMethod = getUc;
                }
            }
            Object uc = getUc != null && profileObj != null ? getUc.invoke(profileObj) : null;
            if (uc == null) return 0L;

            Method getUid = cachedGetClientUserIdMethod;
            if (getUid == null && !getClientUserIdSearched) {
                getClientUserIdSearched = true;
                String mapped = Obfuscate.getMethodName("UserConfig", "getClientUserId");
                getUid = ReflectHelper.findMethodExactIfExists(uc.getClass(), mapped);
                if (getUid == null) {
                    getUid = findZeroArgMethodReturning(uc.getClass(), long.class);
                }
                if (getUid != null) {
                    getUid.setAccessible(true);
                    cachedGetClientUserIdMethod = getUid;
                }
            }
            if (getUid != null) {
                Object res = getUid.invoke(uc);
                if (res instanceof Long) return (Long) res;
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static int resolveCurrentDatacenterId(Object profileObj) {
        try {
            Method getCm = cachedGetConnectionsManagerMethod;
            if (getCm == null && !getConnectionsManagerSearched && profileObj != null) {
                getConnectionsManagerSearched = true;
                Class<?> cmClass = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
                if (cmClass != null) {
                    getCm = findZeroArgMethodReturning(profileObj.getClass(), cmClass);
                    if (getCm != null) cachedGetConnectionsManagerMethod = getCm;
                }
            }
            Object cm = getCm != null && profileObj != null ? getCm.invoke(profileObj) : null;
            if (cm == null) return 0;

            Method getDc = cachedGetCurrentDatacenterIdMethod;
            if (getDc == null && !getCurrentDatacenterIdSearched) {
                getCurrentDatacenterIdSearched = true;
                getDc = ReflectHelper.findMethodExactIfExists(cm.getClass(), "getCurrentDatacenterId");
                if (getDc != null) {
                    getDc.setAccessible(true);
                    cachedGetCurrentDatacenterIdMethod = getDc;
                }
            }
            if (getDc != null) {
                Object res = getDc.invoke(cm);
                if (res instanceof Integer) return (Integer) res;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Method findZeroArgMethodReturning(Class<?> startClass, Class<?> returnType) {
        Class<?> cur = startClass;
        while (cur != null && cur != Object.class) {
            for (Method cand : cur.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(cand.getModifiers())
                        && cand.getParameterTypes().length == 0
                        && cand.getReturnType() == returnType) {
                    cand.setAccessible(true);
                    return cand;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static int readPhotoDcId(Object entity, String entityKey, String photoKey) {
        Object photo = getFieldMappedOrRaw(entity, entityKey, "photo");
        if (photo == null) return 0;
        return readMappedInt(photo, photoKey, "dc_id");
    }

    private static int readFullPhotoDcId(Object profileObj, String infoKey, String photoFieldName) {
        try {
            Field fInfo = findMappedField(profileObj.getClass(), "ProfileActivity", infoKey);
            if (fInfo == null) return 0;
            Object info = fInfo.get(profileObj);
            if (info == null) return 0;
            Field fPhoto = ReflectHelper.findFieldIfExists(info.getClass(), photoFieldName);
            if (fPhoto == null) return 0;
            fPhoto.setAccessible(true);
            Object photo = fPhoto.get(info);
            if (photo == null) return 0;
            return readMappedInt(photo, "TLRPC$Photo", "dc_id");
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Field findMappedField(Class<?> cls, String clsKey, String rawName) {
        String mapped = Obfuscate.getFieldName(clsKey, rawName);
        Field f = ReflectHelper.findFieldIfExists(cls, mapped);
        if (f == null && !rawName.equals(mapped)) {
            f = ReflectHelper.findFieldIfExists(cls, rawName);
        }
        if (f != null) {
            f.setAccessible(true);
        }
        return f;
    }

    private static Object getFieldMappedOrRaw(Object target, String clsKey, String rawName) {
        try {
            Field f = findMappedField(target.getClass(), clsKey, rawName);
            return f != null ? f.get(target) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int readMappedInt(Object target, String clsKey, String rawName) {
        try {
            Field f = findMappedField(target.getClass(), clsKey, rawName);
            if (f == null || f.getType() != int.class) return 0;
            return f.getInt(target);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String readMappedString(Object target, String clsKey, String rawName) {
        Object val = getFieldMappedOrRaw(target, clsKey, rawName);
        return val instanceof String ? (String) val : null;
    }

    private static boolean readMappedBoolean(Object target, String clsKey, String rawName) {
        try {
            Field f = findMappedField(target.getClass(), clsKey, rawName);
            if (f == null || f.getType() != boolean.class) return false;
            return f.getBoolean(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBasicLegacyChat(Object chat) {
        if (chat == null) return false;
        String name = chat.getClass().getName();
        int dollar = name.lastIndexOf('$');
        String simple = dollar >= 0 ? name.substring(dollar + 1) : name;
        return simple.startsWith("TL_chat");
    }

    /** Formats the primary Profile card title in Nagram XF style (plain positive ID without -100; copy keeps -100). */
    public static String formatTitleId(long userId, long chatId) {
        long id = userId != 0 ? userId : chatId;
        return id != 0 ? String.valueOf(Math.abs(id)) : "";
    }

    /** Formats the unspaced Bot API / Telegram numeric ID copied to clipboard. */
    public static String formatRawCopyId(long userId, long chatId, boolean isBasicGroup) {
        if (userId != 0) {
            return String.valueOf(Math.abs(userId));
        }
        if (chatId != 0) {
            long absChat = Math.abs(chatId);
            return (isBasicGroup ? "-" : "-100") + absChat;
        }
        return "";
    }

    /** Formats the Telegram Data Center subtitle line matching Nagram XF ("DC5 Flora, Singapore"). */
    public static String formatDcLine(int dcId) {
        switch (dcId) {
            case 1:
                return "DC1 Pluto, Miami";
            case 2:
                return "DC2 Venus, Amsterdam";
            case 3:
                return "DC3 Aurora, Miami";
            case 4:
                return "DC4 Vesta, Amsterdam";
            case 5:
                return "DC5 Flora, Singapore";
            default:
                return dcId > 5 ? "DC" + dcId : "ID";
        }
    }

    /** Formats estimated account creation date in "~ dd.MM.yy at HH:mm" Nagram XF style. */
    public static String formatApproxDateShort(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy 'at' HH:mm", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "~ " + sdf.format(new Date(timestampMs));
    }

    /** Formats a chat/channel join epoch (seconds) with full date, year, and time. */
    public static String formatChatJoinDate(long epochSeconds, Locale locale) {
        return formatChatJoinDate(epochSeconds, locale, TimeZone.getDefault());
    }

    public static String formatChatJoinDate(long epochSeconds, Locale locale, TimeZone timeZone) {
        Locale loc = locale != null ? locale : Locale.getDefault();
        String pattern = "en".equals(loc.getLanguage()) ? "dd.MM.yyyy 'at' HH:mm" : "dd.MM.yyyy, HH:mm";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
        sdf.setTimeZone(timeZone != null ? timeZone : TimeZone.getDefault());
        return sdf.format(new Date(epochSeconds * 1000L));
    }

    private static CharSequence buildUserCreationHint(long userId, boolean isSelf) {
        IdDateEstimator.AgeResult est = IdDateEstimator.getDate(userId);
        String dateText = formatApproxDateShort(est.timestampMs);
        String template = Translator.get(isSelf ? Keys.ProfileCreatedSelfHint : Keys.ProfileCreatedOtherHint);
        return buildBoldPlaceholderText(template, new String[]{"%s"}, new String[]{dateText});
    }

    /** Resolves the localization key for a channel/group date hint based on creator/membership flags. */
    public static String resolveChatHintKey(boolean isCreator, boolean isLeft) {
        if (isCreator) return Keys.ProfileCreatedChatSelfHint;
        if (isLeft) return Keys.ProfileCreatedChatHint;
        return Keys.ProfileJoinedChatHint;
    }

    private static CharSequence buildChatDateHint(String chatTitle, long joinDateSec, boolean isCreator, boolean isLeft) {
        String safeTitle = (chatTitle != null && !chatTitle.trim().isEmpty()) ? chatTitle.trim() : "this chat";
        String dateText = formatChatJoinDate(joinDateSec, Locale.getDefault());
        String template = Translator.get(resolveChatHintKey(isCreator, isLeft));
        return buildBoldPlaceholderText(template, new String[]{"%1$s", "%2$s"}, new String[]{safeTitle, dateText});
    }

    private static CharSequence buildBoldPlaceholderText(String template, String[] tokens, String[] values) {
        SpannableStringBuilder sb = new SpannableStringBuilder(template);
        for (int i = 0; i < tokens.length && i < values.length; i++) {
            String tok = tokens[i];
            int idx = sb.toString().indexOf(tok);
            if (idx >= 0) {
                String val = values[i];
                sb.replace(idx, idx + tok.length(), val);
                sb.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + val.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private static void showFloatingHint(View anchor, CharSequence message) {
        if (anchor == null || message == null || message.length() == 0) return;
        dismissActiveHint();

        Context ctx = Utils.getCurrentActivity();
        if (ctx == null) ctx = anchor.getContext();
        final float d = ctx.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        int maxBubbleW = Math.min(dm.widthPixels - Math.round(24f * d), Math.round(310f * d));
        int caretH = Math.round(7f * d);
        int padH = Math.round(14f * d);
        int padV = Math.round(9f * d);

        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        tv.setLineSpacing(Math.round(2f * d), 1f);
        tv.setPadding(padH, caretH + padV, padH, padV);
        tv.setMaxWidth(maxBubbleW);

        tv.measure(
                View.MeasureSpec.makeMeasureSpec(maxBubbleW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int bubbleW = tv.getMeasuredWidth();
        int bubbleH = tv.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int anchorCenterX = loc[0] + anchor.getWidth() / 2;
        int margin = Math.round(12f * d);
        int popupX = Math.max(margin, Math.min(anchorCenterX - bubbleW / 2, dm.widthPixels - margin - bubbleW));
        int popupY = loc[1] + anchor.getHeight() - Math.round(3f * d);
        float caretX = Math.max(Math.round(14f * d), Math.min(anchorCenterX - popupX, bubbleW - Math.round(14f * d)));

        tv.setBackground(new TooltipBackgroundDrawable(d, caretX, caretH));

        final PopupWindow popup = new PopupWindow(tv, bubbleW, bubbleH, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(6f * d);
        tv.setOnClickListener(v -> popup.dismiss());

        activeHintPopup = new WeakReference<>(popup);
        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY);
            anchor.postDelayed(() -> {
                if (popup.isShowing()) {
                    try {
                        popup.dismiss();
                    } catch (Throwable ignored) {
                    }
                }
            }, 3500L);
        } catch (Throwable ignored) {
        }
    }

    private static void dismissActiveHint() {
        PopupWindow old = activeHintPopup.get();
        if (old != null && old.isShowing()) {
            try {
                old.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    private static int resolveCalendarAccentColor(View view) {
        int textColor = resolveSecondaryIconColor(view);
        double lum = (0.299 * Color.red(textColor) + 0.587 * Color.green(textColor) + 0.114 * Color.blue(textColor)) / 255.0;
        return lum > 0.5 ? 0xFF52A8F2 : 0xFF3390EC;
    }

    private static int resolveSecondaryIconColor(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            List<TextView> texts = new ArrayList<>();
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView) {
                    texts.add((TextView) child);
                }
            }
            // Prefer valueTextView (child index 1 in TextDetailCell), or fall back to textView / default gray.
            if (texts.size() >= 2) {
                return texts.get(1).getCurrentTextColor();
            }
            if (!texts.isEmpty()) {
                return texts.get(0).getCurrentTextColor();
            }
        }
        return 0xFF7F8C9A;
    }

    private static Object resolveProfileObj(Object adapter) {
        if (adapter == null || outerThisFields == null || profileClass == null) return null;
        for (Field f : outerThisFields) {
            try {
                Object v = f.get(adapter);
                if (profileClass.isInstance(v)) {
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static MessagesController resolveMessagesController(Object profileObj) {
        try {
            Method m = cachedGetMessagesControllerMethod;
            if (m == null && !getMessagesControllerSearched && profileObj != null) {
                getMessagesControllerSearched = true;
                Class<?> mcClass = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
                if (mcClass != null) {
                    m = findZeroArgMethodReturning(profileObj.getClass(), mcClass);
                    if (m != null) {
                        cachedGetMessagesControllerMethod = m;
                    }
                }
            }
            if (m != null && profileObj != null) {
                Object mcObj = m.invoke(profileObj);
                if (mcObj != null) {
                    return new MessagesController(mcObj);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static View extractHolderItemView(Object holder) {
        if (holder == null) return null;
        Field f = cachedViewHolderItemViewField;
        if (f == null) {
            Class<?> cur = holder.getClass();
            while (cur != null && cur != Object.class) {
                for (Field cand : cur.getDeclaredFields()) {
                    if (cand.getType() == View.class) {
                        cand.setAccessible(true);
                        cachedViewHolderItemViewField = cand;
                        f = cand;
                        break;
                    }
                }
                if (f != null) break;
                cur = cur.getSuperclass();
            }
        }
        if (f == null) return null;
        try {
            return (View) f.get(holder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field[] resolveRowFields(Class<?> profileCls, String csv) {
        if (csv == null || csv.isEmpty() || "rowFields".equals(csv)) return new Field[0];
        List<Field> list = new ArrayList<>();
        for (String name : csv.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            Field f = ReflectHelper.findFieldIfExists(profileCls, trimmed);
            if (f != null && f.getType() == int.class) {
                f.setAccessible(true);
                list.add(f);
            }
        }
        return list.toArray(new Field[0]);
    }

    private static Field[] resolveFieldsByCsv(Class<?> cls, String csv) {
        if (csv == null || csv.isEmpty()) return new Field[0];
        List<Field> list = new ArrayList<>();
        for (String name : csv.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            Field f = ReflectHelper.findFieldIfExists(cls, trimmed);
            if (f != null) {
                f.setAccessible(true);
                list.add(f);
            }
        }
        return list.toArray(new Field[0]);
    }

    private static Method findMethodByNameAndSuffixParam(Class<?> cls, String name, Class<?> secondParam) {
        if (cls == null || name == null) return null;
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (name.equals(m.getName()) && p.length == 2 && p[1] == secondParam) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Method findSingleRefParamMethod(Class<?> cls, String name, Class<?> retType) {
        if (cls == null || name == null) return null;
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (name.equals(m.getName()) && p.length == 1 && !p[0].isPrimitive() && m.getReturnType() == retType) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Integer getIdDcRow(Object profileObj) {
        if (profileObj == null) return null;
        synchronized (ID_DC_ROWS) {
            return ID_DC_ROWS.get(profileObj);
        }
    }

    private static void setIdDcRow(Object profileObj, Integer row) {
        if (profileObj == null) return;
        synchronized (ID_DC_ROWS) {
            if (row == null) {
                ID_DC_ROWS.remove(profileObj);
            } else {
                ID_DC_ROWS.put(profileObj, row);
            }
        }
    }

    /**
     * Self-contained 24dp vector-style calendar drawable (no R$drawable dependency on R8 builds).
     */
    private static final class CalendarIconDrawable extends Drawable {
        private final int sizePx;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boxRect = new RectF();

        CalendarIconDrawable(Context ctx, int color) {
            float d = ctx.getResources().getDisplayMetrics().density;
            this.sizePx = Math.round(24f * d);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.8f * d);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setColor(color);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
        }

        @Override
        public int getIntrinsicWidth() {
            return sizePx;
        }

        @Override
        public int getIntrinsicHeight() {
            return sizePx;
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            float l = b.left;
            float t = b.top;

            // Outer rounded box
            boxRect.set(l + w * 0.16f, t + h * 0.22f, l + w * 0.84f, t + h * 0.85f);
            canvas.drawRoundRect(boxRect, w * 0.10f, h * 0.10f, strokePaint);

            // Header divider line
            canvas.drawLine(boxRect.left, t + h * 0.42f, boxRect.right, t + h * 0.42f, strokePaint);

            // Top calendar binding rings
            canvas.drawLine(l + w * 0.34f, t + h * 0.13f, l + w * 0.34f, t + h * 0.27f, strokePaint);
            canvas.drawLine(l + w * 0.66f, t + h * 0.13f, l + w * 0.66f, t + h * 0.27f, strokePaint);

            // Day dots inside body
            float r = w * 0.055f;
            float c1 = l + w * 0.34f;
            float c2 = l + w * 0.50f;
            float c3 = l + w * 0.66f;
            float r1 = t + h * 0.56f;
            float r2 = t + h * 0.71f;
            canvas.drawCircle(c1, r1, r, fillPaint);
            canvas.drawCircle(c2, r1, r, fillPaint);
            canvas.drawCircle(c3, r1, r, fillPaint);
            canvas.drawCircle(c1, r2, r, fillPaint);
            canvas.drawCircle(c2, r2, r, fillPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            strokePaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Dark rounded tooltip bubble with an upward triangle caret aligned to the calendar icon.
     */
    private static final class TooltipBackgroundDrawable extends Drawable {
        private final float density;
        private final float caretX;
        private final int caretH;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path combinedPath = new Path();
        private final Path triPath = new Path();
        private final RectF rect = new RectF();

        TooltipBackgroundDrawable(float density, float caretX, int caretH) {
            this.density = density;
            this.caretX = caretX;
            this.caretH = caretH;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEB232B33);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float r = 10f * density;
            float halfW = 7f * density;
            rect.set(b.left, b.top + caretH, b.right, b.bottom);

            combinedPath.reset();
            combinedPath.addRoundRect(rect, r, r, Path.Direction.CW);

            triPath.reset();
            triPath.moveTo(b.left + caretX - halfW, b.top + caretH + 1f);
            triPath.lineTo(b.left + caretX, b.top);
            triPath.lineTo(b.left + caretX + halfW, b.top + caretH + 1f);
            triPath.close();

            combinedPath.op(triPath, Path.Op.UNION);
            canvas.drawPath(combinedPath, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
