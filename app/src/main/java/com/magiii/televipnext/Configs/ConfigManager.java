package com.magiii.televipnext.Configs;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Clients.Telegraph;
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
    public static ConfigItem language;
    public static ConfigItem hidePinnedMessages;
    public static ConfigItem disableChannelSwipeBack;
    public static ConfigItem disableProfileSwipeBack;
    public static ConfigItem hideProxySponsor;
    public static ConfigItem hideSponsoredAds;
    public static ConfigItem showUserID;
    public static ConfigItem customCalendar;
    public static ConfigItem showTimeSeconds;
    public static ConfigItem alwaysShowProxy;

    // Other Features
    public static ConfigItem otherFeatures;
    public static ConfigItem removesContentSaving;
    public static ConfigItem sayWhereCopyCameFrom;
    public static ConfigItem telegramPremium;
    public static ConfigItem disableNumberRounding;
    public static ConfigItem hideUpdateApp;
    public static ConfigItem fixTLError;

    // Updates
    public static ConfigItem updatesSettings;
    public static ConfigItem autoUpdateCheck;
    public static ConfigItem checkForUpdates;

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

        hideSeenPrivateChat = new ConfigItem(ConfigItem.SWITCH, Keys.HideSeenPrivateChat, ConfigPreferences.getBoolean(Keys.HideSeenPrivateChat), GhostMode::init);
        childrenHideSeen.add(hideSeenPrivateChat);

        hideSeenChannel = new ConfigItem(ConfigItem.SWITCH, Keys.HideSeenChannel, ConfigPreferences.getBoolean(Keys.HideSeenChannel), GhostMode::init);
        childrenHideSeen.add(hideSeenChannel);

        hideSeen = new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.HideSeen, childrenHideSeen);
        items.add(hideSeen);

        markReadAfterSend = new ConfigItem(ConfigItem.SWITCH, Keys.MarkReadAfterSend, ConfigPreferences.getBoolean(Keys.MarkReadAfterSend), GhostMode::init);
        items.add(markReadAfterSend);

        hideTyping = new ConfigItem(ConfigItem.SWITCH, Keys.HideTyping, ConfigPreferences.getBoolean(Keys.HideTyping), GhostMode::init);
        items.add(hideTyping);

        hideStoryView = new ConfigItem(ConfigItem.SWITCH, Keys.HideStoryView, ConfigPreferences.getBoolean(Keys.HideStoryView), GhostMode::init);
        items.add(hideStoryView);

        // Cherrygram already ships native phone number spoiler hiding
        if (!ClientManager.is(ClientManager.Client.Cherrygram)) {
            hidePhone = new ConfigItem(ConfigItem.SWITCH, Keys.HidePhone, true, ConfigPreferences.getBoolean(Keys.HidePhone), HidePhone::init);
            items.add(hidePhone);
        }

        hideOnline = new ConfigItem(ConfigItem.SWITCH, Keys.HideOnline, true, ConfigPreferences.getBoolean(Keys.HideOnline), GhostMode::init);
        items.add(hideOnline);

        onlineInfo = new ConfigItem(ConfigItem.INFO, Keys.OfflineVisibilityInfo);
        items.add(onlineInfo);

        shadows = new ConfigItem(ConfigItem.DIVIDER);
        items.add(shadows);

        // Stories (Cherrygram already ships native Hide Stories)
        if (!ClientManager.is(ClientManager.Client.Cherrygram)) {
            stories = new ConfigItem(ConfigItem.HEADER, Keys.StoriesSettings);
            items.add(stories);

            if (!ConfigPreferences.contains(Keys.HideStoriesChatList)
                    && ConfigPreferences.getBoolean(Keys.DisableStories)) {
                ConfigPreferences.putBoolean(Keys.HideStoriesChatList, true);
                ConfigPreferences.putBoolean(Keys.HideStoriesRings, true);
                ConfigPreferences.putBoolean(Keys.HideStoriesProfile, true);
                ConfigPreferences.putBoolean(Keys.DisableStoriesPosting, true);
            } else if (!ConfigPreferences.contains(Keys.HideStoriesRings)
                    && ConfigPreferences.getBoolean(Keys.HideStoriesProfile)) {
                ConfigPreferences.putBoolean(Keys.HideStoriesRings, true);
            }

            hideStoriesChatList = new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesChatList, ConfigPreferences.getBoolean(Keys.HideStoriesChatList), DisableStories::init);
            items.add(hideStoriesChatList);

            hideStoriesRings = new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesRings, ConfigPreferences.getBoolean(Keys.HideStoriesRings), DisableStories::init);
            items.add(hideStoriesRings);

            hideStoriesProfile = new ConfigItem(ConfigItem.SWITCH, Keys.HideStoriesProfile, ConfigPreferences.getBoolean(Keys.HideStoriesProfile), DisableStories::init);
            items.add(hideStoriesProfile);

            disableStoriesPosting = new ConfigItem(ConfigItem.SWITCH, Keys.DisableStoriesPosting, ConfigPreferences.getBoolean(Keys.DisableStoriesPosting), DisableStories::init);
            items.add(disableStoriesPosting);

            items.add(shadows);
        }

        // Messages
        messages = new ConfigItem(ConfigItem.HEADER, Keys.MessagesSettings);
        items.add(messages);

        if (!ConfigPreferences.contains(Keys.ShowDeletedPrivate)
                && ConfigPreferences.getBoolean(Keys.ShowDeletedMessages)) {
            ConfigPreferences.putBoolean(Keys.ShowDeletedPrivate, true);
            ConfigPreferences.putBoolean(Keys.ShowDeletedGroups, true);
            ConfigPreferences.putBoolean(Keys.ShowDeletedChannels, false);
        }

        List<ConfigItem> childrenDeleted = new ArrayList<>();
        showDeletedPrivate = new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedPrivate, ConfigPreferences.getBoolean(Keys.ShowDeletedPrivate), ShowDeletedMessages::init);
        childrenDeleted.add(showDeletedPrivate);

        showDeletedGroups = new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedGroups, ConfigPreferences.getBoolean(Keys.ShowDeletedGroups), ShowDeletedMessages::init);
        childrenDeleted.add(showDeletedGroups);

        showDeletedChannels = new ConfigItem(ConfigItem.SWITCH, Keys.ShowDeletedChannels, ConfigPreferences.getBoolean(Keys.ShowDeletedChannels), ShowDeletedMessages::init);
        childrenDeleted.add(showDeletedChannels);

        clearDeletedMessages = new ConfigItem(ConfigItem.TEXT, Keys.ClearDeletedMessages);
        childrenDeleted.add(clearDeletedMessages);

        showDeletedMessages = new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.ShowDeletedMessages, childrenDeleted);
        items.add(showDeletedMessages);

        if (!ClientManager.is(ClientManager.Client.NagramXF)) {
            showMessageId = new ConfigItem(ConfigItem.SWITCH, Keys.ShowMessageID, ConfigPreferences.getBoolean(Keys.ShowMessageID), MessageTimeModifier::init);
            items.add(showMessageId);
        }

        saveEditsHistory = new ConfigItem(ConfigItem.SWITCH, Keys.SaveEditsHistory, ConfigPreferences.getBoolean(Keys.SaveEditsHistory), SaveEditsHistory::init);
        items.add(saveEditsHistory);

        items.add(shadows);

        // Connections (Telegraph and Cherrygram already ship native Download & Upload speed boosters)
        if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Cherrygram)) {
            if (!ConfigPreferences.contains(Keys.DownloadSpeed + "Int")
                    && ConfigPreferences.getBoolean(Keys.DownloadSpeed)) {
                ConfigPreferences.putInt(Keys.DownloadSpeed + "Int", DownloadSpeed.MODE_FAST);
            }

            connections = new ConfigItem(ConfigItem.HEADER, Keys.ConnectionsSettings);
            items.add(connections);

            downloadSpeed = new ConfigItem(ConfigItem.TEXT, Keys.DownloadSpeed, false, DownloadSpeed::init);
            items.add(downloadSpeed);

            uploadSpeed = new ConfigItem(ConfigItem.SWITCH, Keys.UploadSpeed, ConfigPreferences.getBoolean(Keys.UploadSpeed), UploadSpeed::init);
            items.add(uploadSpeed);

            items.add(shadows);
        }

        // Media
        media = new ConfigItem(ConfigItem.HEADER, Keys.MediaSettings);
        items.add(media);

        if (!ClientManager.is(ClientManager.Client.Nekogram)) {
            secretMediaSave = new ConfigItem(ConfigItem.SWITCH, Keys.SecretMediaSave, ConfigPreferences.getBoolean(Keys.SecretMediaSave), SecretMediaSave::init);
            items.add(secretMediaSave);
        }

        preventMedia = new ConfigItem(ConfigItem.SWITCH, Keys.PreventMedia, ConfigPreferences.getBoolean(Keys.PreventMedia), PreventMedia::init);
        items.add(preventMedia);

        enableSavingStories = new ConfigItem(ConfigItem.SWITCH, Keys.EnableSavingStories, ConfigPreferences.getBoolean(Keys.EnableSavingStories), EnableSavingStories::init);
        items.add(enableSavingStories);

        enableVoiceMessageSaving = new ConfigItem(ConfigItem.SWITCH, Keys.EnableVoiceMessageSaving, ConfigPreferences.getBoolean(Keys.EnableVoiceMessageSaving), VoiceToMusicHook::init);
        items.add(enableVoiceMessageSaving);

        items.add(shadows);

        // UI
        ui = new ConfigItem(ConfigItem.HEADER, Keys.UiSettings);
        items.add(ui);

        if (!ClientManager.is(ClientManager.Client.Cherrygram) && HideBottomBarTabs.isSupported()) {
            List<ConfigItem> childrenBottomTabs = new ArrayList<>();
            hideBottomTabContacts = new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabContacts,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabContacts), HideBottomBarTabs::init);
            childrenBottomTabs.add(hideBottomTabContacts);

            hideBottomTabSettingsCalls = new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabSettingsCalls,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabSettingsCalls), HideBottomBarTabs::init);
            childrenBottomTabs.add(hideBottomTabSettingsCalls);

            hideBottomTabProfile = new ConfigItem(ConfigItem.SWITCH, Keys.HideBottomTabProfile,
                    ConfigPreferences.getBoolean(Keys.HideBottomTabProfile), HideBottomBarTabs::init);
            childrenBottomTabs.add(hideBottomTabProfile);

            hideBottomBarTabs = new ConfigItem(ConfigItem.EXPANDABLE_SWITCH, Keys.HideBottomBarTabs, childrenBottomTabs);
            items.add(hideBottomBarTabs);
        }

        hideNewChatButton = new ConfigItem(ConfigItem.SWITCH, Keys.HideNewChatButton,
                ConfigPreferences.getBoolean(Keys.HideNewChatButton), HideNewChatButton::init);
        items.add(hideNewChatButton);

        hidePinnedMessages = new ConfigItem(ConfigItem.SWITCH, Keys.HidePinnedMessages, ConfigPreferences.getBoolean(Keys.HidePinnedMessages), HidePinnedMessages::init);
        items.add(hidePinnedMessages);

        disableChannelSwipeBack = new ConfigItem(ConfigItem.SWITCH, Keys.DisableChannelSwipeBack, ConfigPreferences.getBoolean(Keys.DisableChannelSwipeBack), DisableChannelSwipeBack::init);
        items.add(disableChannelSwipeBack);

        disableProfileSwipeBack = new ConfigItem(ConfigItem.SWITCH, Keys.DisableProfileSwipeBack, ConfigPreferences.getBoolean(Keys.DisableProfileSwipeBack), DisableProfileSwipeBack::init);
        items.add(disableProfileSwipeBack);

        hideProxySponsor = new ConfigItem(ConfigItem.SWITCH, Keys.HideProxySponsor, true, ConfigPreferences.getBoolean(Keys.HideProxySponsor), HideProxySponsor::init);
        items.add(hideProxySponsor);

        hideSponsoredAds = new ConfigItem(ConfigItem.SWITCH, Keys.HideSponsoredAds,
                ConfigPreferences.getBoolean(Keys.HideSponsoredAds), HideSponsoredAds::init);
        items.add(hideSponsoredAds);

        if (!hasNativeFullProfileIdDc()) {
            showUserID = new ConfigItem(ConfigItem.SWITCH, Keys.ShowUserID, ConfigPreferences.getBoolean(Keys.ShowUserID), ShowProfileIdDc::init);
            items.add(showUserID);
        }
        if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Nekogram)) {
            showTimeSeconds = new ConfigItem(ConfigItem.SWITCH, Keys.ShowTimeSeconds, ConfigPreferences.getBoolean(Keys.ShowTimeSeconds), MessageTimeModifier::init);
            items.add(showTimeSeconds);
        }
        if (!ClientManager.is(ClientManager.Client.Cherrygram)) {
            disableNumberRounding = new ConfigItem(ConfigItem.SWITCH, Keys.DisableNumberRounding, "5.3K -> 5300", ConfigPreferences.getBoolean(Keys.DisableNumberRounding), DisableNumberRounding::init);
            items.add(disableNumberRounding);
        }
        if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Nekogram)) {
            if (!ConfigPreferences.contains(Keys.AlwaysShowProxy + "Int")
                    && ConfigPreferences.getBoolean(Keys.AlwaysShowProxy)) {
                ConfigPreferences.putInt(Keys.AlwaysShowProxy + "Int", AlwaysShowProxy.MODE_TOP_BAR);
            }
            alwaysShowProxy = new ConfigItem(ConfigItem.TEXT, Keys.AlwaysShowProxy, false, AlwaysShowProxy::init);
            items.add(alwaysShowProxy);
        }
        if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Nekogram) && !ClientManager.is(ClientManager.Client.Cherrygram)) {
            customCalendar = new ConfigItem(ConfigItem.TEXT, Keys.Calendar, true, HijriDate::init);
            items.add(customCalendar);
        }

        language = new ConfigItem(ConfigItem.TEXT, Keys.Language);
        items.add(language);

        items.add(shadows);

        // Other Features
        otherFeatures = new ConfigItem(ConfigItem.HEADER, Keys.OtherFeaturesSettings);
        items.add(otherFeatures);

        removesContentSaving = new ConfigItem(ConfigItem.SWITCH, Keys.RemovesContentSaving, ConfigPreferences.getBoolean(Keys.RemovesContentSaving), RemovesContentSaving::init);
        items.add(removesContentSaving);

        sayWhereCopyCameFrom = new ConfigItem(ConfigItem.SWITCH, Keys.SayWhereCopyCameFrom, ConfigPreferences.getBoolean(Keys.SayWhereCopyCameFrom), null);
        items.add(sayWhereCopyCameFrom);

        telegramPremium = new ConfigItem(ConfigItem.SWITCH, Keys.TelegramPremium, ConfigPreferences.getBoolean(Keys.TelegramPremium), TelePremium::init);
        items.add(telegramPremium);

        hideUpdateApp = new ConfigItem(ConfigItem.SWITCH, Keys.HideUpdateApp, true, ConfigPreferences.getBoolean(Keys.HideUpdateApp), HideUpdateApp::init);
        fixTLError = new ConfigItem(ConfigItem.SWITCH, Keys.FixTLError, ConfigPreferences.getBoolean(Keys.FixTLError), FixTLError::init);
        items.add(hideUpdateApp);
        items.add(fixTLError);

        items.add(shadows);

        // Updates
        if (!ConfigPreferences.contains(Keys.AutoUpdateCheck)) {
            ConfigPreferences.putBoolean(Keys.AutoUpdateCheck, true);
        }
        updatesSettings = new ConfigItem(ConfigItem.HEADER, Keys.UpdatesSettings);
        items.add(updatesSettings);

        autoUpdateCheck = new ConfigItem(ConfigItem.SWITCH, Keys.AutoUpdateCheck,
                ConfigPreferences.getBoolean(Keys.AutoUpdateCheck), null);
        items.add(autoUpdateCheck);

        checkForUpdates = new ConfigItem(ConfigItem.TEXT, Keys.CheckForUpdates);
        items.add(checkForUpdates);

        items.add(shadows);

        btnChannel = new ConfigItem(ConfigItem.TEXT, Keys.DeveloperChannel);
        items.add(btnChannel);

        items.add(shadows);

        btnRestartApp = new ConfigItem(ConfigItem.TEXT, Keys.RestartApp);
        items.add(btnRestartApp);

        items.add(shadows);

        readFeature();

    }

    public static List<ConfigItem> getItems() {
        return items;
    }

    public static void readFeature() {
        try {
            for (ConfigItem item : items) {
                if (item == null) continue;
                if (item.getType() == ConfigItem.EXPANDABLE_SWITCH) {
                    if (item.getChildren() != null) {
                        for (ConfigItem child : item.getChildren()) {
                            if (child != null && child.getType() == ConfigItem.SWITCH && child.isEnable()) {
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
            if (!ClientManager.is(ClientManager.Client.Telegraph) && !ClientManager.is(ClientManager.Client.Nekogram)) {
                AlwaysShowProxy.init();
            }
            if (!hasNativeFullProfileIdDc()) {
                ShowProfileIdDc.init();
            }
            if (!ClientManager.is(ClientManager.Client.Cherrygram) && HideBottomBarTabs.isSupported()) {
                HideBottomBarTabs.init();
            }
            HideNewChatButton.init();
            HideSponsoredAds.init();
            AlwaysSaveMedia.init();
            ShowDeletedMessages.initMenuHooks();

            if (ClientManager.is(ClientManager.Client.Telegraph)) Telegraph.removeAd();

        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private static boolean hasNativeFullProfileIdDc() {
        return ClientManager.is(ClientManager.Client.Cherrygram)
                || ClientManager.is(ClientManager.Client.NagramXF)
                || ClientManager.is(ClientManager.Client.ExteraGram)
                || ClientManager.is(ClientManager.Client.NagramX)
                || ClientManager.is(ClientManager.Client.Octogram);
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
