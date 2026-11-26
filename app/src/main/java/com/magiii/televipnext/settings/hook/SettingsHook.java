package com.magiii.televipnext.settings.hook;


import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Drawable.GhostDrawable;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.obfuscate.auto.DexScanner;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.Adapters.DrawerLayoutAdapter;
import com.magiii.televipnext.virtuals.SettingsIconResolver;
import com.magiii.televipnext.virtuals.messenger.LocaleController;
import com.magiii.televipnext.virtuals.ui.Components.UItem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class SettingsHook {

    private Constructor<?> itemConstructor;

    private static final int NEKOGRAM_SETTINGS_VIEW_TYPE = 8353847;
    private static final int NEKO_SETTINGS_ITEM_ID = 50;
    private static final int TELEVIP_SETTINGS_ITEM_ID = 8353847;

    private static boolean hookNekogramSettings(SettingsController controller) {
        if (!ClientManager.is(ClientManager.Client.Nekogram)) return false;

        Class<?> settings = nekoClass("org.telegram.ui.SettingsActivity");
        Class<?> factory = nekoClass("org.telegram.ui.SettingsActivity$SettingCell$Factory");
        Class<?> cell = nekoClass("org.telegram.ui.SettingsActivity$SettingCell");
        Class<?> item = nekoClass("org.telegram.ui.Components.UItem");
        Class<?> adapter = nekoClass("org.telegram.ui.Components.UniversalAdapter");
        Class<?> list = nekoClass("org.telegram.ui.Components.UniversalRecyclerView");
        if (settings == null || factory == null || cell == null || item == null || adapter == null || list == null) {
            return hookCompiledNekogramSettings(settings, controller);
        }

        Field id = nekoLookup("UItem.id", () -> accessible(item.getDeclaredField("id")));
        Field icon = nekoLookup("UItem.iconResId", () -> accessible(item.getDeclaredField("iconResId")));
        Field iconView = nekoLookup("SettingCell.iconView", () -> accessible(cell.getDeclaredField("iconView")));
        Method createItem = nekoLookup("SettingCell.Factory.of", () -> factory.getDeclaredMethod("of",
                int.class, int.class, int.class, int.class, CharSequence.class, CharSequence.class));
        if (id == null || icon == null || createItem == null) return false;

        if (iconView != null) {
            nekoHook(factory, "bindView", new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) throws Throwable {
                    if (!param.hasThrowable() && id.getInt(param.args[1]) == TELEVIP_SETTINGS_ITEM_ID) {
                        ImageView image = (ImageView) iconView.get(param.args[0]);
                        if (image != null) image.setImageDrawable(new GhostDrawable());
                    }
                }
            }, View.class, item, boolean.class, adapter, list);
        }

        if (!nekoHook(settings, "onClick", new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] != null && id.getInt(param.args[0]) == TELEVIP_SETTINGS_ITEM_ID) {
                    param.setResult(null);
                    controller.openView();
                }
            }
        }, item, View.class, int.class, float.class, float.class)) return false;

        return nekoHook(settings, "fillItems", new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) throws Throwable {
                if (!param.hasThrowable()) addNekogramSettingsItem(param.args[0], item, id, icon, createItem);
            }
        }, ArrayList.class, adapter);
    }

    private static boolean hookCompiledNekogramSettings(Class<?> settings, SettingsController controller) {
        if (settings == null) return false;
        DexScanner scanner = nekoLookup("Nekogram Settings APK DEX", () -> new DexScanner(Utils.sourceDir));
        if (scanner == null) return false;

        String settingsDesc = DexScanner.javaToDesc(settings.getName());
        String[] createViewArgs = {"Landroid/content/Context;"};
        String createView = scanner.findSingleMethod(settingsDesc, false, "Landroid/view/View;", createViewArgs);
        String[] fillArgs = {"Ljava/lang/Object;", "Ljava/lang/Object;"};
        String[] clickArgs = {"Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Number;",
                "Ljava/io/Serializable;", "Ljava/io/Serializable;"};
        String[] factoryArgs = {"I", "I", "I", "I", "Ljava/lang/CharSequence;",
                "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;"};
        String nekoSettingsDesc = "Ltw/nekomimi/nekogram/settings/NekoSettingsActivity;";
        Set<String> callbackTypes = new LinkedHashSet<>();
        for (DexScanner.MethodRef call : scanner.getCalledMethodsInMethod(settingsDesc, createView, createViewArgs, null)) {
            if ("<init>".equals(call.name)) callbackTypes.add(call.ownerDesc);
        }

        DexScanner.MethodRef fill = null;
        DexScanner.MethodRef click = null;
        DexScanner.MethodRef factory = null;
        for (String callbackType : callbackTypes) {
            for (DexScanner.MethodInfo method : scanner.getMethods(callbackType)) {
                if (method.isStatic || !"V".equals(method.returnDesc) || !method.containsConstInt(NEKO_SETTINGS_ITEM_ID)) {
                    continue;
                }
                if (Arrays.equals(method.paramDescs, fillArgs)) {
                    for (DexScanner.MethodRef call : scanner.getCalledMethodsInMethod(callbackType, method.name, fillArgs, null)) {
                        if (!Arrays.equals(call.paramDescs, factoryArgs) || !call.returnDesc.startsWith("L")) continue;
                        if (factory != null && !(callbackType.equals(fill.ownerDesc) && method.name.equals(fill.name)
                                && factory.ownerDesc.equals(call.ownerDesc) && factory.name.equals(call.name))) {
                            Logger.e("Ambiguous Nekogram Settings row factory");
                            return false;
                        }
                        factory = call;
                        fill = new DexScanner.MethodRef(callbackType, method.name, fillArgs, "V");
                    }
                } else if (Arrays.equals(method.paramDescs, clickArgs)
                        && !scanner.getCalledMethodsInMethod(callbackType, method.name, clickArgs, nekoSettingsDesc).isEmpty()) {
                    if (click != null) {
                        Logger.e("Ambiguous Nekogram Settings click callback");
                        return false;
                    }
                    click = new DexScanner.MethodRef(callbackType, method.name, clickArgs, "V");
                }
            }
        }
        if (fill == null || click == null || factory == null) {
            Logger.e("Could not resolve the compiled Nekogram Settings callbacks");
            return false;
        }

        String itemDesc = factory.returnDesc;
        var itemWrites = scanner.getWrittenFieldsInMethod(factory.ownerDesc, factory.name, factoryArgs, itemDesc);
        var clickReads = scanner.getReadInstanceFieldsInMethod(click.ownerDesc, click.name, clickArgs, itemDesc);
        Set<String> rowIdFields = new LinkedHashSet<>();
        for (DexScanner.FieldInfo field : clickReads) {
            if ("I".equals(field.typeDesc)) rowIdFields.add(field.name);
        }
        String idName = null;
        String iconName = null;
        for (DexScanner.FieldInfo field : itemWrites) {
            if (!"I".equals(field.typeDesc)) continue;
            if (rowIdFields.contains(field.name) && idName == null) {
                idName = field.name;
            } else if (!rowIdFields.contains(field.name) && iconName == null) {
                iconName = field.name;
            } else {
                Logger.e("Ambiguous Nekogram Settings item fields");
                return false;
            }
        }

        String captureName = scanner.findSingleField(fill.ownerDesc, false, "Ljava/lang/Object;");
        Field capture = nekoDexField(fill.ownerDesc, captureName);
        Field id = nekoDexField(itemDesc, idName);
        Field icon = nekoDexField(itemDesc, iconName);
        Method fillMethod = nekoDexMethod(fill, Object.class, Object.class);
        Method clickMethod = nekoDexMethod(click, Object.class, Object.class, Number.class,
                java.io.Serializable.class, java.io.Serializable.class);
        Method factoryMethod = nekoDexMethod(factory, int.class, int.class, int.class, int.class,
                CharSequence.class, CharSequence.class, CharSequence.class);
        if (capture == null || id == null || icon == null || fillMethod == null || clickMethod == null
                || factoryMethod == null || !java.lang.reflect.Modifier.isStatic(factoryMethod.getModifiers())) return false;

        Class<?> item = id.getDeclaringClass();
        if (!nekoRegister(clickMethod, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                if (item.isInstance(param.args[0]) && id.getInt(param.args[0]) == TELEVIP_SETTINGS_ITEM_ID) {
                    param.setResult(null);
                    controller.openView();
                }
            }
        })) return false;

        return nekoRegister(fillMethod, new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) throws Throwable {
                if (!param.hasThrowable() && settings.isInstance(capture.get(param.thisObject))) {
                    addNekogramSettingsItem(param.args[0], item, id, icon, factoryMethod);
                }
            }
        });
    }

    private static Field nekoDexField(String owner, String name) {
        return nekoLookup(owner + "#" + name, () -> {
            if (name == null) throw new NoSuchFieldException(owner);
            Class<?> type = Class.forName(DexScanner.descToJava(owner), false, Utils.classLoader);
            return accessible(type.getDeclaredField(name));
        });
    }

    private static Method nekoDexMethod(DexScanner.MethodRef ref, Class<?>... types) {
        return nekoLookup(ref.ownerDesc + "#" + ref.name, () -> {
            Class<?> owner = Class.forName(DexScanner.descToJava(ref.ownerDesc), false, Utils.classLoader);
            Method method = owner.getDeclaredMethod(ref.name, types);
            method.setAccessible(true);
            return method;
        });
    }

    @SuppressWarnings("unchecked")
    private static void addNekogramSettingsItem(Object list, Class<?> item, Field id, Field icon, Method factory)
            throws ReflectiveOperationException {
        if (!(list instanceof ArrayList<?>)) return;
        ArrayList<Object> items = (ArrayList<Object>) list;
        int nekoPosition = -1;
        for (int i = 0; i < items.size(); i++) {
            Object entry = items.get(i);
            if (!item.isInstance(entry)) continue;
            int itemId = id.getInt(entry);
            if (itemId == TELEVIP_SETTINGS_ITEM_ID) return;
            if (itemId == NEKO_SETTINGS_ITEM_ID) nekoPosition = i;
        }
        if (nekoPosition < 0) return;

        int nativeIcon = icon.getInt(items.get(nekoPosition));
        Object[] args = {TELEVIP_SETTINGS_ITEM_ID, 0xFFF46F6F, 0xFFDF5555, nativeIcon,
                formatEntryTitle("TeleVip Next \uD83D\uDC7B", LocaleController.isRTL()), null, null};
        Object entry = factory.invoke(null, Arrays.copyOf(args, factory.getParameterCount()));
        if (item.isInstance(entry)) items.add(nekoPosition + 1, entry);
    }

    private static Class<?> nekoClass(String name) {
        return nekoLookup(name, () -> Class.forName(name, false, Utils.classLoader));
    }

    private static void hookNekogramProfile(SettingsController controller) {
        if (!ClientManager.is(ClientManager.Client.Nekogram)) return;

        Class<?> profile = nekoLookup(ClassNames.PROFILE_ACTIVITY,
                () -> Class.forName(ClassNames.PROFILE_ACTIVITY, false, Utils.classLoader));
        if (profile == null) return;

        Class<?> adapter = nekoLookup("ProfileActivity.ListAdapter",
                () -> Class.forName(profile.getName() + "$ListAdapter", false, Utils.classLoader));
        Class<?> diff = nekoLookup("ProfileActivity.DiffCallback",
                () -> Class.forName(profile.getName() + "$DiffCallback", false, Utils.classLoader));
        Class<?> holder = nekoLookup("RecyclerView.ViewHolder",
                () -> Class.forName("androidx.recyclerview.widget.RecyclerView$ViewHolder", false, Utils.classLoader));
        Class<?> textCell = nekoLookup("TextCell",
                () -> Class.forName("org.telegram.ui.Cells.TextCell", false, Utils.classLoader));
        if (adapter == null || diff == null || holder == null || textCell == null) return;

        DexScanner scanner = nekoLookup("Nekogram APK DEX", () -> new DexScanner(Utils.sourceDir));
        if (scanner == null) return;

        Field settingsSection = nekoLookup("ProfileActivity.settingsSectionRow",
                () -> accessible(profile.getDeclaredField("settingsSectionRow")));
        Field rowCount = nekoLookup("ProfileActivity.rowCount", () -> accessible(profile.getDeclaredField("rowCount")));
        Field versionRow = nekoLookup("ProfileActivity.versionRow", () -> accessible(profile.getDeclaredField("versionRow")));
        Field bottomPadding = nekoLookup("ProfileActivity.bottomPaddingRow", () -> accessible(profile.getDeclaredField("bottomPaddingRow")));
        Field nativeTextType = nekoLookup("ListAdapter.VIEW_TYPE_TEXT", () -> accessible(adapter.getDeclaredField("VIEW_TYPE_TEXT")));
        Field adapterProfile = nekoLookup("ListAdapter enclosing ProfileActivity", () -> enclosingProfile(adapter, profile));
        Field diffProfile = nekoLookup("DiffCallback enclosing ProfileActivity", () -> enclosingProfile(diff, profile));
        Field itemView = nekoLookup("ViewHolder.itemView", () -> holder.getField("itemView"));
        Method position = nekoLookup("ViewHolder.getAdapterPosition", () -> holder.getMethod("getAdapterPosition"));
        Method setText = nekoLookup("TextCell.setText used by ListAdapter", () -> nekoCellSetText(scanner, adapter, holder, textCell));
        Method click = nekoLookup("ProfileActivity.createView list click callback", () -> nekoProfileClick(scanner, profile));
        if (settingsSection == null || rowCount == null || versionRow == null || bottomPadding == null || nativeTextType == null
                || adapterProfile == null || diffProfile == null || itemView == null
                || position == null || setText == null || click == null) return;

        Integer textType = nekoLookup("ListAdapter.VIEW_TYPE_TEXT value", () -> nativeTextType.getInt(null));
        if (textType == null) return;

        WeakHashMap<Object, Integer> rows = new WeakHashMap<>();
        // Activate row injection only after the adapter, diff, and click hooks are installed.
        if (!nekoHook(adapter, "getItemViewType", new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(rows.get(adapterProfile.get(param.thisObject)))) {
                    param.setResult(NEKOGRAM_SETTINGS_VIEW_TYPE);
                }
            }
        }, int.class)) return;

        if (!nekoHook(adapter, "onCreateViewHolder", new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                if (Integer.valueOf(NEKOGRAM_SETTINGS_VIEW_TYPE).equals(param.args[1])) {
                    param.args[1] = textType;
                }
            }
        }, ViewGroup.class, int.class)) return;

        if (!nekoHook(adapter, "onBindViewHolder", new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                if (param.args[1].equals(rows.get(adapterProfile.get(param.thisObject)))) {
                    String title = formatEntryTitle("TeleVip Next 👻", LocaleController.isRTL());
                    setText.invoke(itemView.get(param.args[0]), title, false);
                    param.setResult(null);
                }
            }
        }, holder, int.class)) return;

        if (!nekoHook(adapter, "isEnabled", new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) throws Throwable {
                Integer row = rows.get(adapterProfile.get(param.thisObject));
                if (row != null && row.equals(position.invoke(param.args[0]))) {
                    param.setResult(true);
                }
            }
        }, holder)) return;

        if (!nekoHook(diff, "fillPositions", new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) throws Throwable {
                if (param.hasThrowable()) return;
                Integer row = rows.get(diffProfile.get(param.thisObject));
                if (row != null) {
                    ((SparseIntArray) param.args[0]).put(row, NEKOGRAM_SETTINGS_VIEW_TYPE);
                }
            }
        }, SparseIntArray.class)) return;

        if (!nekoRegister(click, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                if (param.args[1].equals(rows.get(param.thisObject))) {
                    param.setResult(null);
                    controller.openView();
                }
            }
        })) return;

        nekoHook(profile, "updateRowsIds", new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) throws Throwable {
                if (param.hasThrowable()) return;
                Object activity = param.thisObject;
                rows.remove(activity);
                int count = rowCount.getInt(activity);
                int version = versionRow.getInt(activity);
                int padding = bottomPadding.getInt(activity);
                // In this settings layout, the version footer is followed only by bottom padding.
                int section = settingsSection.getInt(activity);
                if (section < 0 || section >= version || count == Integer.MAX_VALUE
                        || padding != version + 1 || padding != count - 1) return;

                bottomPadding.setInt(activity, count);
                try {
                    rowCount.setInt(activity, count + 1);
                } catch (Throwable t) {
                    bottomPadding.setInt(activity, padding);
                    throw t;
                }
                rows.put(activity, padding);
            }
        });
    }

    private static Method nekoCellSetText(DexScanner scanner, Class<?> adapter, Class<?> holder, Class<?> textCell)
            throws Exception {
        String[] bindSignature = {DexScanner.javaToDesc(holder.getName()), "I"};
        for (DexScanner.MethodRef call : scanner.getCalledMethodsInMethod(DexScanner.javaToDesc(adapter.getName()),
                "onBindViewHolder", bindSignature, DexScanner.javaToDesc(textCell.getName()))) {
            if (!"setText".equals(call.name) || !"V".equals(call.returnDesc)
                    || call.paramDescs.length != 2 || !"Z".equals(call.paramDescs[1])) continue;
            Class<?> labelType = Class.forName(DexScanner.descToJava(call.paramDescs[0]), false, Utils.classLoader);
            if (labelType.isAssignableFrom(String.class)) {
                return textCell.getMethod(call.name, labelType, boolean.class);
            }
        }
        throw new NoSuchMethodException("TextCell.setText used by ListAdapter.onBindViewHolder");
    }

    private static Method nekoProfileClick(DexScanner scanner, Class<?> profile) throws Exception {
        String[] signature = {"Landroid/view/View;", "I", "F", "F"};
        String name = null;
        for (DexScanner.MethodInfo method : scanner.getMethods(DexScanner.javaToDesc(profile.getName()))) {
            if (!method.isStatic && "V".equals(method.returnDesc) && Arrays.equals(signature, method.paramDescs)
                    && method.readsFieldNamed("affiliateRow")) {
                if (name != null) throw new NoSuchMethodException("Ambiguous ProfileActivity list click callback");
                name = method.name;
            }
        }
        if (name == null) throw new NoSuchMethodException("ProfileActivity list click callback (View, int, float, float)");
        return profile.getDeclaredMethod(name, View.class, int.class, float.class, float.class);
    }

    private static Field enclosingProfile(Class<?> nested, Class<?> profile) throws NoSuchFieldException {
        for (Field field : nested.getDeclaredFields()) {
            if (field.isSynthetic() && field.getType() == profile) return accessible(field);
        }
        throw new NoSuchFieldException(nested.getName() + " enclosing " + profile.getName());
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    private static boolean nekoHook(Class<?> owner, String name, BaseMethodHook callback, Class<?>... types) {
        Method method = nekoLookup(owner.getName() + "#" + name, () -> owner.getDeclaredMethod(name, types));
        return method != null && nekoRegister(method, callback);
    }

    private static boolean nekoRegister(Method method, BaseMethodHook callback) {
        try {
            XposedCompat.hookMethod(method, callback);
            return true;
        } catch (Throwable t) {
            XposedCompat.log(Log.ERROR, "TeleVip", "Nekogram settings hook: " + method, t);
            return false;
        }
    }

    private interface NekogramLookup<T> {
        T resolve() throws Throwable;
    }

    private static <T> T nekoLookup(String member, NekogramLookup<T> lookup) {
        try {
            return lookup.resolve();
        } catch (Throwable t) {
            XposedCompat.log(Log.ERROR, "TeleVip", "Nekogram settings lookup: " + member, t);
            return null;
        }
    }


    public void newSettings(Class<?> SettingsActivityClass, Class<?> SettingsActivity$SettingCell$FactoryClass, SettingsController settingsController){
        boolean nekogramSettingsHooked = hookNekogramSettings(settingsController);
        hookNekogramProfile(settingsController);
        if (nekogramSettingsHooked) return;
        try {

            GhostDrawable ghostDrawable = new GhostDrawable();

            Class<?> settingCellClass = ClassLoad.getClass(ClassNames.SETTINGS_ACTIVITY_SETTING_CELL, Utils.classLoader, false);
            if (settingCellClass != null) {
                XposedCompat.hookAllMethods(settingCellClass, Obfuscate.getMethodName("SettingsActivity$SettingCell", "set"), new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (param.args.length >= 3 && param.args[2] instanceof Integer && ((Integer) param.args[2]) == 8353847) {
                            ImageView iconView = findSettingCellIconView(param.thisObject);
                            if (iconView != null) {
                                iconView.setImageDrawable(ghostDrawable);
                            }
                        }
                    }
                });
            } else {
                String bindName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "bindView");
                if (bindName != null) {
                    XposedCompat.hookAllMethods(SettingsActivity$SettingCell$FactoryClass, bindName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (param.args.length >= 2 && param.args[0] instanceof View && param.args[1] != null) {
                                UItem uItem = new UItem(param.args[1]);
                                if (uItem.getID() == 8353847) {
                                    ImageView iconView = findSettingCellIconView(param.args[0]);
                                    if (iconView != null) {
                                        iconView.setImageDrawable(ghostDrawable);
                                    }
                                }
                            }
                        }
                    });
                }
            }

            final Class<?> UItemClass = ClassLoad.getClass(ClassNames.UITEM);

            final String ofName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "of");
            XposedCompat.hookAllMethods(SettingsActivityClass, Obfuscate.getMethodName("SettingsActivity", "fillItems"),
                    new BaseMethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterMethod(final MethodHookParam param) {
                            ArrayList<Object> arrayList = null;
                            for (Object arg : param.args) {
                                if (arg instanceof ArrayList) {
                                    arrayList = (ArrayList<Object>) arg;
                                    break;
                                }
                            }
                            if (arrayList != null) {
                                int color1 = 0xFFF46F6F;
                                int color2 = 0xFFDF5555;

                                Object uItem = null;
                                try {
                                    String entryTitle = formatEntryTitle("TeleVip Next \uD83D\uDC7B", LocaleController.isRTL());
                                    Method m6 = ReflectHelper.findMethodExactIfExists(SettingsActivity$SettingCell$FactoryClass, ofName,
                                            int.class, int.class, int.class, int.class, CharSequence.class, CharSequence.class);
                                    if (m6 != null) {
                                        uItem = m6.invoke(null, 8353847, color1, color2, 8353847,
                                                entryTitle, null);
                                    } else {
                                        for (Method fm : SettingsActivity$SettingCell$FactoryClass.getDeclaredMethods()) {
                                            Class<?>[] pt = fm.getParameterTypes();
                                            if (fm.getName().equals(ofName) && pt.length == 7
                                                    && pt[0] == int.class && pt[1] == int.class
                                                    && pt[2] == int.class && pt[3] == int.class
                                                    && pt[4] == CharSequence.class && pt[5] == CharSequence.class) {
                                                fm.setAccessible(true);
                                                Object arg6 = pt[6] == boolean.class ? Boolean.FALSE
                                                        : (pt[6] == int.class ? Integer.valueOf(0) : null);
                                                uItem = fm.invoke(null, 8353847, color1, color2, 8353847,
                                                        entryTitle, null, arg6);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    Logger.e(t);
                                }
                                if (uItem == null) return;

                                for (int i = 0; i < arrayList.size(); i++) {
                                    UItem item = new UItem(arrayList.get(i));

                                    if (item.getText() != null && item.getSubtext() != null) {
                                        arrayList.add(i, uItem);
                                        Object shadowItem = createShadowItem(arrayList, UItemClass);
                                        if (shadowItem != null) {
                                            arrayList.add(i + 1, shadowItem);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    });

            String onClickName = Obfuscate.getMethodName("SettingsActivity", "onClick");
            XposedCompat.hookAllMethods(
                    SettingsActivityClass,
                    onClickName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(final MethodHookParam param) {
                            Object rawItem = null;
                            for (Object arg : param.args) {
                                if (arg != null && UItemClass != null && UItemClass.isInstance(arg)) {
                                    rawItem = arg;
                                    break;
                                }
                            }
                            if (rawItem != null) {
                                UItem uItem = new UItem(rawItem);
                                if (uItem.getID() == 8353847) {
                                    Logger.w("SettingsHook: opening TeleVip Settings via " + param.method.getName());
                                    settingsController.openView();
                                }
                            }
                        }
                    });
        } catch (Throwable t){
            Logger.e(t);
        }
    }

    private static String formatEntryTitle(String title, boolean telegramRtl) {
        if (!telegramRtl || title == null || title.isEmpty()) return title;
        return "\u200F\u2066" + title + "\u2069";
    }

    private static Object createShadowItem(ArrayList<Object> arrayList, Class<?> uItemClass) {
        if (uItemClass == null) return null;
        String idFieldName = Obfuscate.getFieldName("UItem", "id");
        try {
            Method asShadow = ReflectHelper.findMethodExactIfExists(uItemClass, "asShadow", CharSequence.class);
            if (asShadow != null && Modifier.isStatic(asShadow.getModifiers())) {
                asShadow.setAccessible(true);
                Object shadow = asShadow.invoke(null, (CharSequence) null);
                if (shadow != null) {
                    setShadowId(uItemClass, shadow, idFieldName);
                    return shadow;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field viewTypeField = findViewTypeField(uItemClass, idFieldName);
            if (viewTypeField == null) return null;

            Integer shadowViewType = findShadowViewType(arrayList, uItemClass, viewTypeField);
            if (shadowViewType == null) return null;

            Object shadow;
            try {
                Constructor<?> ctor2 = uItemClass.getDeclaredConstructor(int.class, boolean.class);
                ctor2.setAccessible(true);
                shadow = ctor2.newInstance(shadowViewType, false);
            } catch (NoSuchMethodException e) {
                Constructor<?> ctor1 = uItemClass.getDeclaredConstructor(int.class);
                ctor1.setAccessible(true);
                shadow = ctor1.newInstance(shadowViewType);
            }
            if (shadow != null) {
                setShadowId(uItemClass, shadow, idFieldName);
            }
            return shadow;
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static Field findViewTypeField(Class<?> uItemClass, String idFieldName) {
        Field firstNonStaticInt = null;
        for (Class<?> c = uItemClass.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                f.setAccessible(true);
                if (Modifier.isFinal(f.getModifiers())) {
                    return f;
                }
                if (firstNonStaticInt == null) {
                    firstNonStaticInt = f;
                }
            }
        }
        if (firstNonStaticInt != null) return firstNonStaticInt;

        for (Field f : uItemClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            if (idFieldName != null && idFieldName.equals(f.getName())) continue;
            f.setAccessible(true);
            if (Modifier.isFinal(f.getModifiers())) {
                return f;
            }
            if (firstNonStaticInt == null) {
                firstNonStaticInt = f;
            }
        }
        return firstNonStaticInt;
    }

    private static Integer findShadowViewType(ArrayList<Object> arrayList, Class<?> uItemClass, Field viewTypeField) {
        if (arrayList == null) return null;
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object raw : arrayList) {
            if (raw == null || !uItemClass.isInstance(raw)) continue;
            UItem item = new UItem(raw);
            if (item.getText() == null && item.getSubtext() == null) {
                try {
                    int vt = viewTypeField.getInt(raw);
                    Integer prev = counts.get(vt);
                    counts.put(vt, prev == null ? 1 : prev + 1);
                } catch (Throwable ignored) {}
            }
        }
        Integer bestVt = null;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int vt = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && vt > 0 && (bestVt == null || bestVt <= 0))) {
                bestVt = vt;
                bestCount = count;
            }
        }
        return bestVt;
    }

    private static void setShadowId(Class<?> uItemClass, Object shadow, String idFieldName) {
        if (shadow == null || idFieldName == null) return;
        try {
            Field idField = ReflectHelper.findFieldIfExists(uItemClass, idFieldName);
            if (idField != null) {
                idField.setAccessible(true);
                idField.setInt(shadow, 8353848);
            }
        } catch (Throwable ignored) {}
    }

    private static ImageView findSettingCellIconView(Object cell) {
        if (cell == null) return null;
        try {
            Field iconField = ReflectHelper.findFieldIfExists(
                    cell.getClass(), Obfuscate.getFieldName("SettingsActivity$SettingCell", "iconView"));
            if (iconField != null) {
                iconField.setAccessible(true);
                Object v = iconField.get(cell);
                if (v instanceof ImageView) return (ImageView) v;
            }
        } catch (Throwable ignored) {}

        if (cell instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) cell;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof FrameLayout) {
                    FrameLayout fl = (FrameLayout) child;
                    for (int j = 0; j < fl.getChildCount(); j++) {
                        View inner = fl.getChildAt(j);
                        if (inner instanceof ImageView) {
                            return (ImageView) inner;
                        }
                    }
                }
            }
        }

        for (Field f : cell.getClass().getDeclaredFields()) {
            if (ImageView.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(cell);
                    if (v instanceof ImageView) return (ImageView) v;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public void oldSettings(SettingsController settingsController){
        boolean nekogramSettingsHooked = hookNekogramSettings(settingsController);
        hookNekogramProfile(settingsController);
        if (nekogramSettingsHooked) return;
        final Class<?> itemClass = ReflectHelper.findClassIfExists(Obfuscate.getClassName("org.telegram.ui.Adapters.DrawerLayoutAdapter$Item"), Utils.classLoader);

        if (itemClass != null) {
            HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.DRAWER_LAYOUT_ADAPTER),
                    Obfuscate.getMethodName("DrawerLayoutAdapter", "resetItems"),
                    new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) throws Throwable {

                            DrawerLayoutAdapter drawerLayoutAdapter = new DrawerLayoutAdapter(param.thisObject);

                            ArrayList<?> items = drawerLayoutAdapter.getItems();

                            if (itemConstructor == null) {
                                itemConstructor = itemClass.getDeclaredConstructor(ArgsResolver.resolveObject("item", new Class[]{int.class, CharSequence.class, int.class}));
                                itemConstructor.setAccessible(true);
                            }

                            Object newItem = itemConstructor.newInstance(
                                    8353847,
                                    formatEntryTitle("TeleVip Next \uD83D\uDC7B", LocaleController.isRTL()),
                                    SettingsIconResolver.getIconSettings());

                            if (items instanceof ArrayList<?>) {
                                ArrayList<Object> typedItems = (ArrayList<Object>) items;
                                typedItems.add(newItem);
                            }
                        }
                    }
            );

            BaseMethodHook onCreateHook = new BaseMethodHook() {
                @Override
                protected void afterMethod(final MethodHookParam param) {

                    Object Launch = param.thisObject;

                    Object drawerLayoutAdapter = ReflectHelper.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutAdapter"));
                    if (drawerLayoutAdapter != null) {
                        Object args = param.args[1];

                        int id = (int) ReflectHelper.callMethod(drawerLayoutAdapter, Obfuscate.getMethodName("DrawerLayoutAdapter", "getId"), args);
                        if (id == 8353847) {

                            Object drawerLayoutContainer = ReflectHelper.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutContainer"));
                            if (drawerLayoutContainer != null) {
                                if (!ClientManager.is(ClientManager.Client.ForkgramClassic)) {
                                    ReflectHelper.callMethod(drawerLayoutContainer, Obfuscate.getMethodName("DrawerLayoutContainer", "closeDrawer"));
                                } else {
                                    ReflectHelper.callMethod(drawerLayoutContainer, Obfuscate.getMethodName("DrawerLayoutContainer", "closeDrawer"), true);
                                }
                            }

                            settingsController.openView();
                        }

                    }
                }
            };

            if (ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY) != null) {

                Method onCreateMethod = null;
                for (Method method : ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY).getDeclaredMethods()) {
                    if (Arrays.equals(method.getParameterTypes(), ArgsResolver.resolveObject("onCreateMethod", new Class[]{android.view.View.class, int.class, float.class, float.class}))) {
                        onCreateMethod = method;
                        break;
                    }
                }

                if (onCreateMethod == null) {
                    Logger.w("Failed to hook onCreateMethod! Reason: No method found, " + Utils.issue);
                    return;
                }

                XposedCompat.hookMethod(onCreateMethod, onCreateHook);
            }
        }

    }
}

