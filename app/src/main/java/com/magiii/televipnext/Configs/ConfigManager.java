package com.magiii.televipnext.Configs;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Clients.Telegraph;
import com.magiii.televipnext.features.ui.AlwaysShowDownloads;
import com.magiii.televipnext.features.ui.AlwaysShowProxy;
import com.magiii.televipnext.features.ui.DisableChannelSwipeBack;
import com.magiii.televipnext.features.ui.DisableNumberRounding;
import com.magiii.televipnext.features.ui.DisableProfileSwipeBack;
import com.magiii.televipnext.features.stories.DisableStories;
import com.magiii.televipnext.features.connections.DownloadSpeed;
import com.magiii.televipnext.features.connections.UploadSpeed;
import com.magiii.televipnext.features.media.EnableSavingStories;
import com.magiii.televipnext.features.other.FixTLError;
import com.magiii.televipnext.features.ghostMode.GhostMode;
import com.magiii.televipnext.features.ghostMode.HidePhone;
import com.magiii.televipnext.features.ui.HidePinnedMessages;
import com.magiii.televipnext.features.ui.HideProxySponsor;
import com.magiii.televipnext.features.ui.HideSponsoredAds;
import com.magiii.televipnext.features.ui.HideBottomBarTabs;
import com.magiii.televipnext.features.ui.HideNewChatButton;
import com.magiii.televipnext.features.other.HideUpdateApp;
import com.magiii.televipnext.features.ui.HijriDate;
import com.magiii.televipnext.features.media.PreventMedia;
import com.magiii.televipnext.features.other.RemovesContentSaving;
import com.magiii.televipnext.features.messages.SaveEditsHistory;
import com.magiii.televipnext.features.media.SecretMediaSave;
import com.magiii.televipnext.features.other.TelePremium;
import com.magiii.televipnext.features.media.VoiceToMusicHook;
import com.magiii.televipnext.features.messages.MessageTimeModifier;
import com.magiii.televipnext.features.messages.ShowDeletedMessages;
import com.magiii.televipnext.features.otherFeatures.AlwaysSaveMedia;
import com.magiii.televipnext.features.otherFeatures.CopyNameHook;
import com.magiii.televipnext.features.otherFeatures.EditOnlineTextView;
import com.magiii.televipnext.features.otherFeatures.FeatureInitializer;
import com.magiii.televipnext.features.otherFeatures.ShowProfileIdDc;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.logging.Logger;

import java.util.ArrayList;
import java.util.List;


public class ConfigManager {
    

    private static final List<ConfigItem> items = new ArrayList<>();


    // GhostMode
    public static ConfigItem ghostModeSettings;
    public static ConfigItem hideSeenPrivateChat;
    public static ConfigItem hideSeenChannel;
    public static ConfigItem hideSeen;
    public static ConfigItem markReadAfterSend;
    public static ConfigItem hideTyping;
    public static ConfigItem hideStoryView;
    public static ConfigItem hidePhone;
    public static ConfigItem hideOnline;
    public static ConfigItem onlineInfo;

    public static ConfigItem shadows;

    // Stories
    public static ConfigItem stories;
    public static ConfigItem hideStoriesChatList;
    public static ConfigItem hideStoriesRings;
    public static ConfigItem hideStoriesProfile;
    public static ConfigItem disableStoriesPosting;

    // Messages
    public static ConfigItem messages;
    public static ConfigItem showDeletedPrivate;
    public static ConfigItem showDeletedGroups;
    public static ConfigItem showDeletedChannels;
    public static ConfigItem clearDeletedMessages;
    public static ConfigItem showDeletedMessages;
    public static ConfigItem showMessageId;
    public static ConfigItem saveEditsHistory;

    // Connections
    public static ConfigItem connections;
    public static ConfigItem downloadSpeed;
    public static ConfigItem uploadSpeed;

    // Media
    public static ConfigItem media;
    public static ConfigItem secretMediaSave;
    public static ConfigItem preventMedia;
    public static ConfigItem enableSavingStories;
    public static ConfigItem enableVoiceMessageSaving;

    //UI
    public static ConfigItem ui;
    public static ConfigItem hideBottomBarTabs;
    public static ConfigItem hideBottomTabContacts;
    public static ConfigItem hideBottomTabSettingsCalls;
    public static ConfigItem hideBottomTabProfile;
    public static ConfigItem hideNewChatButton;
    public static ConfigItem hidePinnedMessages;
    public static ConfigItem disableChannelSwipeBack;
    public static ConfigItem disableProfileSwipeBack;
    public static ConfigItem hideProxySponsor;
    public static ConfigItem hideSponsoredAds;
    public static ConfigItem showUserID;
    public static ConfigItem customCalendar;
    public static ConfigItem showTimeSeconds;
    public static ConfigItem alwaysShowProxy;
    public static ConfigItem alwaysShowDownloads;

    // Other Features
    public static ConfigItem otherFeatures;
    public static ConfigItem removesContentSaving;
    public static ConfigItem sayWhereCopyCameFrom;
    public static ConfigItem telegramPremium;
    public static ConfigItem disableNumberRounding;
    public static ConfigItem hideUpdateApp;
    public static ConfigItem fixTLError;

    // Button
    public static ConfigItem btnChannel;
    public static ConfigItem btnRestartApp;

    public static void loadAndRead(){
        ConfigPreferences.init();
        load();
    }

    public static void load() {
        items.clear();

        // GhostMode
        ghostModeSettings = new ConfigItem(ConfigItem.HEADER, Keys.GhostModeSettings);
        items.add(ghostModeSettings);

        List<ConfigItem> childrenHideSeen = new ArrayList<>();

        hideSeenPrivateChat = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideSeenPrivateChat, ConfigPreferences.getBoolean(Keys.HideSeenPrivateChat), GhostMode::init));
        childrenHideSeen.add(hideSeenPrivateChat);

        hideSeenChannel = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideSeenChannel, ConfigPreferences.getBoolean(Keys.HideSeenChannel), GhostMode::init));
        childrenHideSeen.add(hideSeenChannel);

        hideSeen = bindNative(new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.HideSeen, childrenHideSeen));
        items.add(hideSeen);

        markReadAfterSend = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.MarkReadAfterSend, ConfigPreferences.getBoolean(Keys.MarkReadAfterSend), GhostMode::init));
        items.add(markReadAfterSend);

        hideTyping = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideTyping, ConfigPreferences.getBoolean(Keys.HideTyping), GhostMode::init));
        items.add(hideTyping);

        hideStoryView = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideStoryView, ConfigPreferences.getBoolean(Keys.HideStoryView), GhostMode::init));
        items.add(hideStoryView);

        hidePhone = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HidePhone, true, ConfigPreferences.getBoolean(Keys.HidePhone), HidePhone::init));
        items.add(hidePhone);

        hideOnline = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideOnline, true, ConfigPreferences.getBoolean(Keys.HideOnline), GhostMode::init));
        items.add(hideOnline);

        onlineInfo = new ConfigItem(ConfigItem.INFO, Keys.OfflineVisibilityInfo);
        items.add(onlineInfo);

        shadows = new ConfigItem(ConfigItem.DIVIDER);
        items.add(shadows);

        // Stories
        stories = new ConfigItem(ConfigItem.HEADER, Keys.StoriesSettings);
        items.add(stories);

        if (!isNativeKey(Keys.HideStoriesChatList)
                && !ConfigPreferences.contains(Keys.HideStoriesChatList)
                && ConfigPreferences.getBoolean(Keys.DisableStories)) {
            ConfigPreferences.putBoolean(Keys.HideStoriesChatList, true);
            if (!isNativeKey(Keys.HideStoriesRings)) {
                ConfigPreferences.putBoolean(Keys.HideStoriesRings, true);
            }
            if (!isNativeKey(Keys.HideStoriesProfile)) {
                ConfigPreferences.putBoolean(Keys.HideStoriesProfile, true);
            }
            if (!isNativeKey(Keys.DisableStoriesPosting)) {
                ConfigPreferences.putBoolean(Keys.DisableStoriesPosting, true);
            }
        } else if (!isNativeKey(Keys.HideStoriesRings)
                && !ConfigPreferences.contains(Keys.HideStoriesRings)
                && ConfigPreferences.getBoolean(Keys.HideStoriesProfile)) {
            ConfigPreferences.putBoolean(Keys.HideStoriesRings, true);
        }

        hideStoriesChatList = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesChatList, ConfigPreferences.getBoolean(Keys.HideStoriesChatList), DisableStories::init));
        items.add(hideStoriesChatList);

        hideStoriesRings = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesRings, ConfigPreferences.getBoolean(Keys.HideStoriesRings), DisableStories::init));
        items.add(hideStoriesRings);

        hideStoriesProfile = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesProfile, ConfigPreferences.getBoolean(Keys.HideStoriesProfile), DisableStories::init));
        items.add(hideStoriesProfile);

        disableStoriesPosting = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.DisableStoriesPosting, ConfigPreferences.getBoolean(Keys.DisableStoriesPosting), DisableStories::init));
        items.add(disableStoriesPosting);

        items.add(shadows);

        // Messages
        messages = new ConfigItem(ConfigItem.HEADER, Keys.MessagesSettings);
        items.add(messages);

        if (!isNativeKey(Keys.ShowDeletedPrivate)
                && !ConfigPreferences.contains(Keys.ShowDeletedPrivate)
                && ConfigPreferences.getBoolean(Keys.ShowDeletedMessages)) {
            ConfigPreferences.putBoolean(Keys.ShowDeletedPrivate, true);
            if (!isNativeKey(Keys.ShowDeletedGroups)) {
                ConfigPreferences.putBoolean(Keys.ShowDeletedGroups, true);
            }
            if (!isNativeKey(Keys.ShowDeletedChannels)) {
                ConfigPreferences.putBoolean(Keys.ShowDeletedChannels, false);
            }
        }

        List<ConfigItem> childrenDeleted = new ArrayList<>();
        showDeletedPrivate = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedPrivate, ConfigPreferences.getBoolean(Keys.ShowDeletedPrivate), ShowDeletedMessages::init));
        childrenDeleted.add(showDeletedPrivate);

        showDeletedGroups = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedGroups, ConfigPreferences.getBoolean(Keys.ShowDeletedGroups), ShowDeletedMessages::init));
        childrenDeleted.add(showDeletedGroups);

        showDeletedChannels = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedChannels, ConfigPreferences.getBoolean(Keys.ShowDeletedChannels), ShowDeletedMessages::init));
        childrenDeleted.add(showDeletedChannels);

        clearDeletedMessages = bindNative(new ConfigItem(ConfigItem.TEXT, Keys.ClearDeletedMessages));
        childrenDeleted.add(clearDeletedMessages);

        showDeletedMessages = bindNative(new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.ShowDeletedMessages, childrenDeleted));
        items.add(showDeletedMessages);

        showMessageId = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowMessageID, ConfigPreferences.getBoolean(Keys.ShowMessageID), MessageTimeModifier::init));
        items.add(showMessageId);

        saveEditsHistory = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.SaveEditsHistory, ConfigPreferences.getBoolean(Keys.SaveEditsHistory), SaveEditsHistory::init));
        items.add(saveEditsHistory);

        items.add(shadows);

        // Connections
        if (!isNativeKey(Keys.DownloadSpeed)
                && !ConfigPreferences.contains(Keys.DownloadSpeed + "Int")
                && ConfigPreferences.getBoolean(Keys.DownloadSpeed)) {
            ConfigPreferences.putInt(Keys.DownloadSpeed + "Int", DownloadSpeed.MODE_FAST);
        }

        connections = new ConfigItem(ConfigItem.HEADER, Keys.ConnectionsSettings);
        items.add(connections);

        downloadSpeed = bindNative(new ConfigItem(ConfigItem.TEXT, Keys.DownloadSpeed, false, DownloadSpeed::init));
        items.add(downloadSpeed);

        uploadSpeed = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.UploadSpeed, ConfigPreferences.getBoolean(Keys.UploadSpeed), UploadSpeed::init));
        items.add(uploadSpeed);

        items.add(shadows);

        // Media
        media = new ConfigItem(ConfigItem.HEADER, Keys.MediaSettings);
        items.add(media);

        secretMediaSave = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.SecretMediaSave, ConfigPreferences.getBoolean(Keys.SecretMediaSave), SecretMediaSave::init));
        items.add(secretMediaSave);

        preventMedia = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.PreventMedia, ConfigPreferences.getBoolean(Keys.PreventMedia), PreventMedia::init));
        items.add(preventMedia);

        enableSavingStories = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.EnableSavingStories, ConfigPreferences.getBoolean(Keys.EnableSavingStories), EnableSavingStories::init));
        items.add(enableSavingStories);

        enableVoiceMessageSaving = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.EnableVoiceMessageSaving, ConfigPreferences.getBoolean(Keys.EnableVoiceMessageSaving), VoiceToMusicHook::init));
        items.add(enableVoiceMessageSaving);

        items.add(shadows);

        // UI
        ui = new ConfigItem(ConfigItem.HEADER, Keys.UiSettings);
        items.add(ui);

        hideBottomBarTabs = null;
        hideBottomTabContacts = null;
        hideBottomTabSettingsCalls = null;
        hideBottomTabProfile = null;
        if (HideBottomBarTabs.isSupported() || hasNativeHideBottomBarTabs()) {
            List<ConfigItem> childrenBottomTabs = new ArrayList<>();
            hideBottomTabContacts = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabContacts,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabContacts), HideBottomBarTabs::init));
            childrenBottomTabs.add(hideBottomTabContacts);

            hideBottomTabSettingsCalls = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabSettingsCalls,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabSettingsCalls), HideBottomBarTabs::init));
            childrenBottomTabs.add(hideBottomTabSettingsCalls);

            hideBottomTabProfile = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabProfile,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabProfile), HideBottomBarTabs::init));
            childrenBottomTabs.add(hideBottomTabProfile);

            hideBottomBarTabs = bindNative(new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.HideBottomBarTabs, childrenBottomTabs));
            items.add(hideBottomBarTabs);
        }

        hideNewChatButton = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideNewChatButton,
                ConfigPreferences.getBoolean(Keys.HideNewChatButton), HideNewChatButton::init));
        items.add(hideNewChatButton);

        hidePinnedMessages = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HidePinnedMessages, ConfigPreferences.getBoolean(Keys.HidePinnedMessages), HidePinnedMessages::init));
        items.add(hidePinnedMessages);

        disableChannelSwipeBack = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.DisableChannelSwipeBack, ConfigPreferences.getBoolean(Keys.DisableChannelSwipeBack), DisableChannelSwipeBack::init));
        items.add(disableChannelSwipeBack);

        disableProfileSwipeBack = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.DisableProfileSwipeBack, ConfigPreferences.getBoolean(Keys.DisableProfileSwipeBack), DisableProfileSwipeBack::init));
        items.add(disableProfileSwipeBack);

        hideProxySponsor = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideProxySponsor, true, ConfigPreferences.getBoolean(Keys.HideProxySponsor), HideProxySponsor::init));
        items.add(hideProxySponsor);

        hideSponsoredAds = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideSponsoredAds,
                ConfigPreferences.getBoolean(Keys.HideSponsoredAds), HideSponsoredAds::init));
        items.add(hideSponsoredAds);

        showUserID = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowUserID, ConfigPreferences.getBoolean(Keys.ShowUserID), ShowProfileIdDc::init));
        items.add(showUserID);

        showTimeSeconds = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.ShowTimeSeconds, ConfigPreferences.getBoolean(Keys.ShowTimeSeconds), MessageTimeModifier::init));
        items.add(showTimeSeconds);

        disableNumberRounding = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.DisableNumberRounding, "5.3K -> 5300", ConfigPreferences.getBoolean(Keys.DisableNumberRounding), DisableNumberRounding::init));
        items.add(disableNumberRounding);

        if (!isNativeKey(Keys.AlwaysShowProxy)
                && !ConfigPreferences.contains(Keys.AlwaysShowProxy + "Int")
                && ConfigPreferences.getBoolean(Keys.AlwaysShowProxy)) {
            ConfigPreferences.putInt(Keys.AlwaysShowProxy + "Int", AlwaysShowProxy.MODE_TOP_BAR);
        }
        alwaysShowProxy = bindNative(new ConfigItem(ConfigItem.TEXT, Keys.AlwaysShowProxy, false, AlwaysShowProxy::init));
        items.add(alwaysShowProxy);

        alwaysShowDownloads = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.AlwaysShowDownloads,
                ConfigPreferences.getBoolean(Keys.AlwaysShowDownloads), AlwaysShowDownloads::init));
        items.add(alwaysShowDownloads);

        customCalendar = bindNative(new ConfigItem(ConfigItem.TEXT, Keys.Calendar, true, HijriDate::init));
        items.add(customCalendar);

        items.add(shadows);

        // Other Features
        otherFeatures = new ConfigItem(ConfigItem.HEADER, Keys.OtherFeaturesSettings);
        items.add(otherFeatures);

        removesContentSaving = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.RemovesContentSaving, ConfigPreferences.getBoolean(Keys.RemovesContentSaving), RemovesContentSaving::init));
        items.add(removesContentSaving);

        sayWhereCopyCameFrom = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.SayWhereCopyCameFrom, ConfigPreferences.getBoolean(Keys.SayWhereCopyCameFrom), null));
        items.add(sayWhereCopyCameFrom);

        telegramPremium = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.TelegramPremium, ConfigPreferences.getBoolean(Keys.TelegramPremium), TelePremium::init));
        items.add(telegramPremium);

        hideUpdateApp = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.HideUpdateApp, true, ConfigPreferences.getBoolean(Keys.HideUpdateApp), HideUpdateApp::init));
        fixTLError = bindNative(new ConfigItem(ConfigItem.SWITCH, Keys.FixTLError, ConfigPreferences.getBoolean(Keys.FixTLError), FixTLError::init));
        items.add(hideUpdateApp);
        items.add(fixTLError);

        items.add(shadows);

        // Ensure auto-update check defaults to enabled (controlled in Credits sheet)
        if (!ConfigPreferences.contains(Keys.AutoUpdateCheck)) {
            ConfigPreferences.putBoolean(Keys.AutoUpdateCheck, true);
        }

        btnChannel = new ConfigItem(ConfigItem.TEXT, Keys.DeveloperChannel);
        items.add(btnChannel);

        items.add(shadows);

        btnRestartApp = new ConfigItem(ConfigItem.TEXT, Keys.RestartApp);
        items.add(btnRestartApp);

        items.add(shadows);

        readFeature();

    }

    private static ConfigItem bindNative(ConfigItem item) {
        if (item == null || item.getKey() == null) return item;
        NativeFeatureTarget target = NativeFeatureRegistry.resolve(ClientManager.getCurrent(), item.getKey());
        if (target != null) {
            item.setNativeTarget(target);
        }
        return item;
    }

    private static boolean isNativeKey(String key) {
        return NativeFeatureRegistry.resolve(ClientManager.getCurrent(), key) != null;
    }

    private static boolean hasNativeHideBottomBarTabs() {
        return isNativeKey(Keys.HideBottomBarTabs)
                || isNativeKey(Keys.HideBottomTabContacts)
                || isNativeKey(Keys.HideBottomTabSettingsCalls)
                || isNativeKey(Keys.HideBottomTabProfile);
    }

    public static List<ConfigItem> getItems() {
        return items;
    }

    public static void readFeature() {
        try {
            for (ConfigItem item : items) {
                if (item == null || item.isNativeBuiltIn()) continue;
                if (item.getType() == ConfigItem.EXPANDABLE_SWITCH) {
                    if (item.getChildren() != null) {
                        for (ConfigItem child : item.getChildren()) {
                            if (child != null && !child.isNativeBuiltIn()
                                    && child.getType() == ConfigItem.SWITCH && child.isEnable()) {
                                child.run();
                            }
                        }
                    }
                    continue;
                }
                if (item.getType() != ConfigItem.SWITCH && item.getIntValue() == 0) continue;
                if (item.isEnable() || (item.getType() == ConfigItem.TEXT && item.getIntValue() > 0)) {
                    item.run();
                }
            }

            if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Nekogram) && !ClientManager.is(ClientManager.Client.Cherrygram)) {
                FeatureInitializer.init();
                CopyNameHook.init();
                EditOnlineTextView.init();
            }
            if (alwaysShowProxy != null && !alwaysShowProxy.isNativeBuiltIn()) {
                AlwaysShowProxy.init();
            }
            if (alwaysShowDownloads != null && !alwaysShowDownloads.isNativeBuiltIn()) {
                AlwaysShowDownloads.init();
            }
            if (showUserID != null && !showUserID.isNativeBuiltIn()) {
                ShowProfileIdDc.init();
            }
            if (hideBottomBarTabs != null && !hideBottomBarTabs.isNativeBuiltIn()
                    && !hideBottomBarTabs.isAllSwitchChildrenNative() && HideBottomBarTabs.isSupported()) {
                HideBottomBarTabs.init();
            }
            if (hideNewChatButton != null && !hideNewChatButton.isNativeBuiltIn()) {
                HideNewChatButton.init();
            }
            if (hideSponsoredAds != null && !hideSponsoredAds.isNativeBuiltIn()) {
                HideSponsoredAds.init();
            }
            AlwaysSaveMedia.init();
            if (showDeletedMessages != null && !showDeletedMessages.isNativeBuiltIn()
                    && !showDeletedMessages.isAllSwitchChildrenNative()) {
                ShowDeletedMessages.initMenuHooks();
            }

            if (ClientManager.is(ClientManager.Client.Telegraph)) Telegraph.removeAd();

        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static boolean isShowDeletedAnyEnabled() {
        return (showDeletedPrivate != null && showDeletedPrivate.isEnable()) ||
                (showDeletedGroups != null && showDeletedGroups.isEnable()) ||
                (showDeletedChannels != null && showDeletedChannels.isEnable());
    }

    public static boolean isGhostMode() {
        return (hideSeen != null && hideSeen.isEnable()) ||
                (hideSeenPrivateChat != null && hideSeenPrivateChat.isEnable()) ||
                (hideSeenChannel != null && hideSeenChannel.isEnable()) ||
                (hideStoryView != null && hideStoryView.isEnable()) ||
                (hideTyping != null && hideTyping.isEnable()) ||
                (hideOnline != null && hideOnline.isEnable()) ||
                (markReadAfterSend != null && markReadAfterSend.isEnable());
    }

}
