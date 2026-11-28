package com.magiii.televipnext.Configs;

import java.util.List;

public class ConfigItem {

    public static final int HEADER = 0,
            SWITCH = 1,
            TEXT = 2,
            DIVIDER = 3,
            INFO = 4,
            EXPANDABLE_SWITCH = 5;

    private final int type;
    private String key;
    private String value;
    private boolean restartRequired;
    private boolean enable;
    private Runnable runnable;
    private List<ConfigItem> children;
    private NativeFeatureTarget nativeTarget;

    public ConfigItem(int type, String key, String value, boolean enable, Runnable runnable) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.enable = enable;
        this.runnable = runnable;
    }

    public ConfigItem(int type, String key, boolean restartRequired, boolean enable, Runnable runnable) {
        this.type = type;
        this.key = key;
        this.restartRequired = restartRequired;
        this.enable = enable;
        this.runnable = runnable;
    }

    public ConfigItem(int type, String key, boolean enable, Runnable runnable) {
        this.type = type;
        this.key = key;
        this.enable = enable;
        this.runnable = runnable;
    }

    public ConfigItem(int type, String key, List<ConfigItem> children) {
        this.type = type;
        this.key = key;
        this.children = children;
        boolean any = false;
        if (children != null) {
            for (ConfigItem child : children) {
                if (child != null && child.getType() == SWITCH && !child.isNativeBuiltIn() && child.isEnable()) {
                    any = true;
                    break;
                }
            }
        }
        enable = any;
    }

    public ConfigItem(int type, String key) {
        this.type = type;
        this.key = key;
    }

    public ConfigItem(int type) {
        this.type = type;
    }

    public int getType() { return type; }
    public String getKey() { return key; }
    public String getValue() { return value; }

    public List<ConfigItem> getChildren() { return children; }

    public boolean isNativeBuiltIn() { return nativeTarget != null; }

    public NativeFeatureTarget getNativeTarget() { return nativeTarget; }

    public ConfigItem setNativeTarget(NativeFeatureTarget target) {
        this.nativeTarget = target;
        if (target != null) {
            this.enable = false;
        }
        return this;
    }

    public int getActiveSwitchChildCount() {
        if (children == null) return 0;
        int count = 0;
        for (ConfigItem child : children) {
            if (child != null && child.getType() == SWITCH && !child.isNativeBuiltIn()) {
                count++;
            }
        }
        return count;
    }

    public int getEnabledActiveSwitchChildCount() {
        if (children == null) return 0;
        int count = 0;
        for (ConfigItem child : children) {
            if (child != null && child.getType() == SWITCH && !child.isNativeBuiltIn() && child.isEnable()) {
                count++;
            }
        }
        return count;
    }

    public boolean isAllSwitchChildrenNative() {
        if (children == null) return false;
        int switchCount = 0;
        for (ConfigItem child : children) {
            if (child != null && child.getType() == SWITCH) {
                switchCount++;
                if (!child.isNativeBuiltIn()) {
                    return false;
                }
            }
        }
        return switchCount > 0;
    }

    public NativeFeatureTarget getUnifiedNativeChildrenTarget() {
        if (nativeTarget != null) {
            return nativeTarget;
        }
        if (!isAllSwitchChildrenNative() || children == null) {
            return null;
        }
        NativeFeatureTarget unified = null;
        for (ConfigItem child : children) {
            if (child == null) continue;
            if (child.getType() == SWITCH) {
                NativeFeatureTarget childTarget = child.getNativeTarget();
                if (childTarget == null) return null;
                if (unified == null) {
                    unified = childTarget;
                } else if (!unified.hasSameDestination(childTarget)) {
                    return null;
                }
            } else {
                // Non-switch child (e.g. ClearDeletedMessages TEXT) must also share the same target to unify
                if (!child.isNativeBuiltIn() || unified == null
                        || !unified.hasSameDestination(child.getNativeTarget())) {
                    return null;
                }
            }
        }
        return unified;
    }

    public boolean isEnable() {
        if (nativeTarget != null) return false;
        return enable;
    }

    public boolean isRestartRequired() { return restartRequired; }

    public void setEnable(boolean value) {
        if (nativeTarget != null) return;
        this.enable = value;
        ConfigPreferences.putBoolean(key, value);
    }

    public int getIntValue() {
        if (nativeTarget != null) return 0;
        return ConfigPreferences.getInt(key + "Int");
    }

    public void setIntValue(int value) {
        if (nativeTarget != null) return;
        ConfigPreferences.putInt(key + "Int", value);
    }

    public int getCustomCalendar() { return getIntValue(); }

    public void setCustomCalendar(int value) {
        setIntValue(value);
    }

    public void run() {
        if (nativeTarget != null) return;
        if (runnable != null) runnable.run();
    }

}