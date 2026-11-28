package com.magiii.televipnext.Configs;

import android.os.Bundle;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable descriptor encapsulating how to open a client's native settings screen
 * and locate/highlight the exact row corresponding to a TeleVip Next feature.
 */
public final class NativeFeatureTarget {

    public enum LaunchMode {
        /** Direct BaseFragment subclass instantiated via constructor. */
        FRAGMENT,
        /** Wrapper BaseFragment taking a delegate entry instance (e.g. OctoGram PreferencesFragment). */
        WRAPPER_FRAGMENT,
        /** Static factory method returning a BaseFragment. */
        STATIC_FACTORY,
        /** ExteraGram SettingsRegistry.getInstance().handleLink(alias, null). */
        EXTERA_REGISTRY,
        /** Standalone android.app.Activity launched via Intent. */
        ANDROID_ACTIVITY,
        /** Feature is built-in with no navigable settings screen. */
        INFO_ONLY
    }

    public enum NativeScrollMode {
        NONE,
        CHERRYGRAM_SLUG,
        NEKO_SCROLL_TO_ROW,
        NEKOGRAM_SCROLL_TO_ROW,
        MOMO_SET_SCROLL_TO,
        OCTO_WRAPPER_KEY
    }

    private static final String EXTERA_SETTINGS_REGISTRY =
            "com.exteragram.messenger.preferences.utils.SettingsRegistry";
    private static final String[] EMPTY_STRINGS = new String[0];
    private static final Object[] EMPTY_OBJECTS = new Object[0];
    private static final int[] EMPTY_INTS = new int[0];

    public final LaunchMode launchMode;
    public final String targetClassName;
    public final String[] fallbackClassNames;
    public final String wrapperClassName;
    public final String factoryMethodName;
    public final Object[] ctorArgs;
    public final Bundle activityExtras;
    public final NativeScrollMode nativeScrollMode;
    public final String nativeScrollKey;
    public final String[] rowStringResKeys;
    public final String[] rowFallbackTexts;
    public final String[] rowFieldNames;
    public final int[] uItemIds;
    public final String sectionHint;
    public final NativeFeatureTarget fallbackTarget;

    private NativeFeatureTarget(Builder b) {
        this.launchMode = b.launchMode != null ? b.launchMode : LaunchMode.INFO_ONLY;
        this.targetClassName = b.targetClassName;
        this.fallbackClassNames = b.fallbackClassNames != null ? b.fallbackClassNames.clone() : EMPTY_STRINGS;
        this.wrapperClassName = b.wrapperClassName;
        this.factoryMethodName = b.factoryMethodName;
        this.ctorArgs = b.ctorArgs != null ? b.ctorArgs.clone() : EMPTY_OBJECTS;
        this.activityExtras = b.activityExtras != null ? new Bundle(b.activityExtras) : null;
        this.nativeScrollMode = b.nativeScrollMode != null ? b.nativeScrollMode : NativeScrollMode.NONE;
        this.nativeScrollKey = b.nativeScrollKey;
        this.rowStringResKeys = b.rowStringResKeys != null ? b.rowStringResKeys.clone() : EMPTY_STRINGS;
        this.rowFallbackTexts = b.rowFallbackTexts != null ? b.rowFallbackTexts.clone() : EMPTY_STRINGS;
        this.rowFieldNames = b.rowFieldNames != null ? b.rowFieldNames.clone() : EMPTY_STRINGS;
        this.uItemIds = b.uItemIds != null ? b.uItemIds.clone() : EMPTY_INTS;
        this.sectionHint = b.sectionHint;
        this.fallbackTarget = b.fallbackTarget;
    }

    public boolean isJumpable() {
        return launchMode != LaunchMode.INFO_ONLY
                && targetClassName != null
                && !targetClassName.isEmpty();
    }

    /**
     * Checks whether two targets point to the exact same destination screen and row.
     */
    public boolean hasSameDestination(NativeFeatureTarget other) {
        if (this == other) return true;
        if (other == null) return false;
        return launchMode == other.launchMode
                && Objects.equals(targetClassName, other.targetClassName)
                && Objects.equals(wrapperClassName, other.wrapperClassName)
                && Objects.equals(factoryMethodName, other.factoryMethodName)
                && nativeScrollMode == other.nativeScrollMode
                && Objects.equals(nativeScrollKey, other.nativeScrollKey)
                && Objects.equals(firstOrNull(rowStringResKeys), firstOrNull(other.rowStringResKeys))
                && Objects.equals(firstOrNull(rowFallbackTexts), firstOrNull(other.rowFallbackTexts))
                && Objects.equals(firstOrNull(rowFieldNames), firstOrNull(other.rowFieldNames))
                && Arrays.equals(uItemIds, other.uItemIds);
    }

    private static String firstOrNull(String[] arr) {
        return (arr != null && arr.length > 0) ? arr[0] : null;
    }

    public static Builder fragment(String className) {
        return new Builder(LaunchMode.FRAGMENT, className);
    }

    public static Builder wrapperFragment(String wrapperClassName, String entryClassName) {
        Builder b = new Builder(LaunchMode.WRAPPER_FRAGMENT, entryClassName);
        b.wrapperClassName = wrapperClassName;
        return b;
    }

    public static Builder factory(String ownerClassName, String methodName, Object... args) {
        Builder b = new Builder(LaunchMode.STATIC_FACTORY, ownerClassName);
        b.factoryMethodName = methodName;
        if (args != null && args.length > 0) {
            b.ctorArgs = args;
        }
        return b;
    }

    public static Builder exteraRegistry(String alias) {
        Builder b = new Builder(LaunchMode.EXTERA_REGISTRY, EXTERA_SETTINGS_REGISTRY);
        b.nativeScrollKey = alias;
        return b;
    }

    public static Builder activity(String activityClassName) {
        return new Builder(LaunchMode.ANDROID_ACTIVITY, activityClassName);
    }

    public static NativeFeatureTarget infoOnly(String sectionHint) {
        return new Builder(LaunchMode.INFO_ONLY, null)
                .hint(sectionHint)
                .build();
    }

    public static final class Builder {
        private final LaunchMode launchMode;
        private final String targetClassName;
        private String[] fallbackClassNames;
        private String wrapperClassName;
        private String factoryMethodName;
        private Object[] ctorArgs;
        private Bundle activityExtras;
        private NativeScrollMode nativeScrollMode = NativeScrollMode.NONE;
        private String nativeScrollKey;
        private String[] rowStringResKeys;
        private String[] rowFallbackTexts;
        private String[] rowFieldNames;
        private int[] uItemIds;
        private String sectionHint;
        private NativeFeatureTarget fallbackTarget;

        private Builder(LaunchMode launchMode, String targetClassName) {
            this.launchMode = launchMode;
            this.targetClassName = targetClassName;
        }

        public Builder args(Object... ctorArgs) {
            this.ctorArgs = ctorArgs;
            return this;
        }

        public Builder extras(Bundle activityExtras) {
            this.activityExtras = activityExtras;
            return this;
        }

        public Builder fallbackClasses(String... classes) {
            this.fallbackClassNames = classes;
            return this;
        }

        public Builder nativeScroll(NativeScrollMode mode, String key) {
            this.nativeScrollMode = mode != null ? mode : NativeScrollMode.NONE;
            this.nativeScrollKey = key;
            return this;
        }

        public Builder resKeys(String... keys) {
            this.rowStringResKeys = keys;
            return this;
        }

        public Builder texts(String... fallbackTexts) {
            this.rowFallbackTexts = fallbackTexts;
            return this;
        }

        public Builder rowFields(String... fieldNames) {
            this.rowFieldNames = fieldNames;
            return this;
        }

        public Builder uItemIds(int... ids) {
            this.uItemIds = ids;
            return this;
        }

        public Builder hint(String sectionHint) {
            this.sectionHint = sectionHint;
            return this;
        }

        public Builder fallback(NativeFeatureTarget fallback) {
            this.fallbackTarget = fallback;
            return this;
        }

        public NativeFeatureTarget build() {
            return new NativeFeatureTarget(this);
        }
    }
}
