package com.magiii.televipnext.Configs;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.NativeFeatureTarget.NativeScrollMode;
import com.magiii.televipnext.language.Keys;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized 14-client matrix mapping (Client, TeleVip featureKey) -> NativeFeatureTarget.
 * Only strictly free, user-facing, full native equivalents are registered here.
 */
public final class NativeFeatureRegistry {

    private static final Map<ClientManager.Client, Map<String, NativeFeatureTarget>> REGISTRY =
            new EnumMap<>(ClientManager.Client.class);

    static {
        registerCherrygram();
        registerExteraGram();
        registerOctogram();
        registerNagram();
        registerNagramX();
        registerNagramXF();
        registerNekogram();
        registerMomogram();
        registerForkgramFamily();
        registerTelegraph();
        registerIMeFamily();
        registerMercurygram();
        registerNicegram();
        // Official Telegram (Telegram, TelegramWeb, TelegramBeta) and unlisted forks have 0 native entries.
    }

    private NativeFeatureRegistry() {}

    /**
     * Returns the NativeFeatureTarget if the given TeleVip feature key is natively built into the client,
     * or null if TeleVip Next should keep the option enabled and hook-active.
     */
    public static NativeFeatureTarget resolve(ClientManager.Client client, String featureKey) {
        if (client == null || featureKey == null) return null;
        Map<String, NativeFeatureTarget> map = REGISTRY.get(client);
        return map != null ? map.get(featureKey) : null;
    }

    private static void put(ClientManager.Client client, String key, NativeFeatureTarget target) {
        Map<String, NativeFeatureTarget> map = REGISTRY.get(client);
        if (map == null) {
            map = new HashMap<>();
            REGISTRY.put(client, map);
        }
        map.put(key, target);
    }

    private static void putAll(ClientManager.Client client, NativeFeatureTarget target, String... keys) {
        for (String key : keys) {
            put(client, key, target);
        }
    }

    private static void registerShared(Map<String, NativeFeatureTarget> map, ClientManager.Client... clients) {
        Map<String, NativeFeatureTarget> readOnly = Collections.unmodifiableMap(map);
        for (ClientManager.Client client : clients) {
            REGISTRY.put(client, readOnly);
        }
    }

    // =========================================================================
    // 1. Cherrygram (uz.unnarsx.cherrygram)
    // =========================================================================
    private static void registerCherrygram() {
        final ClientManager.Client c = ClientManager.Client.Cherrygram;
        final String general = "uz.unnarsx.cherrygram.preferences.GeneralPreferencesEntry";
        final String msgProf = "uz.unnarsx.cherrygram.preferences.MessagesAndProfilesPreferencesEntry";
        final String mainTabs = "uz.unnarsx.cherrygram.preferences.tabs.MainTabsPreferencesEntry";

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(msgProf)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "hidePhoneNumber")
                .texts("Hide phone numbers")
                .hint("Cherrygram > Messages & Profiles")
                .build());

        NativeFeatureTarget cgStories = NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "hideStories")
                .texts("Hide Stories (locally)", "Hide Stories")
                .hint("Cherrygram > General")
                .build();
        putAll(c, cgStories,
                Keys.HideStoriesChatList,
                Keys.HideStoriesRings,
                Keys.HideStoriesProfile,
                Keys.DisableStoriesPosting);

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "downloadSpeedBoost")
                .texts("Download speed boost")
                .hint("Cherrygram > General")
                .build());

        put(c, Keys.UploadSpeed, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "uploadSpeedBoost")
                .texts("Upload speed boost")
                .hint("Cherrygram > General")
                .build());

        NativeFeatureTarget cgTabs = NativeFeatureTarget.fragment(mainTabs)
                .texts("Bottom tabs organizer")
                .hint("Cherrygram > Bottom tabs organizer")
                .build();
        putAll(c, cgTabs,
                Keys.HideBottomBarTabs,
                Keys.HideBottomTabContacts,
                Keys.HideBottomTabSettingsCalls,
                Keys.HideBottomTabProfile);

        put(c, Keys.ShowUserID, NativeFeatureTarget.fragment(msgProf)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "showIdDc")
                .texts("Show ID and DC in profile")
                .hint("Cherrygram > Messages & Profiles")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(msgProf)
                .nativeScroll(NativeScrollMode.CHERRYGRAM_SLUG, "showSeconds")
                .texts("Format time with seconds")
                .hint("Cherrygram > Messages & Profiles")
                .build());
    }

    // =========================================================================
    // 2. ExteraGram (com.exteragram.messenger)
    // =========================================================================
    private static void registerExteraGram() {
        final ClientManager.Client c = ClientManager.Client.ExteraGram;
        final String general = "com.exteragram.messenger.preferences.GeneralPreferencesActivity";
        final String appearance = "com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity";

        put(c, Keys.HidePhone, extera("hidePhoneNumber", general,
                "HidePhoneNumber", "Hide Phone Number", "exteraGram > General"));

        NativeFeatureTarget exteraStories = extera("hideStories", appearance,
                "HideStories", "Hide Stories", "exteraGram > Appearance");
        putAll(c, exteraStories,
                Keys.HideStoriesChatList,
                Keys.HideStoriesRings,
                Keys.HideStoriesProfile,
                Keys.DisableStoriesPosting);

        put(c, Keys.DownloadSpeed, extera("downloadSpeedBoost", general,
                "DownloadSpeedBoost", "Download Speed Boost", "exteraGram > General"));

        put(c, Keys.UploadSpeed, extera("uploadSpeedBoost", general,
                "UploadSpeedBoost", "Upload Speed Boost", "exteraGram > General"));

        put(c, Keys.HideNewChatButton, extera("hideFloatingButton", appearance,
                "HideFloatingButton", "Hide Floating Button", "exteraGram > Appearance"));

        put(c, Keys.ShowUserID, extera("showIdAndDc", general,
                "ShowIdAndDc", "Show ID and DC", "exteraGram > General"));

        put(c, Keys.ShowTimeSeconds, extera("formatTimeWithSeconds", general,
                "FormatTimeWithSeconds", "Format Time with Seconds", "exteraGram > General"));

        put(c, Keys.DisableNumberRounding, extera("disableNumberRounding", general,
                "DisableNumberRounding", "Disable Number Rounding", "exteraGram > General"));
    }

    private static NativeFeatureTarget extera(
            String alias, String fallbackFragment, String resKey, String fallbackText, String hint) {
        NativeFeatureTarget directFallback = NativeFeatureTarget.fragment(fallbackFragment)
                .resKeys(resKey)
                .texts(fallbackText)
                .hint(hint)
                .build();
        return NativeFeatureTarget.exteraRegistry(alias)
                .resKeys(resKey)
                .texts(fallbackText)
                .hint(hint)
                .fallback(directFallback)
                .build();
    }

    // =========================================================================
    // 3. Octogram (it.octogram.android)
    // =========================================================================
    private static void registerOctogram() {
        final ClientManager.Client c = ClientManager.Client.Octogram;
        final String wrapper = "it.octogram.android.PreferencesFragment";
        final String privacy = "it.octogram.android.PrivacyPreferencesEntry";
        final String general = "it.octogram.android.GeneralPreferencesEntry";
        final String experiments = "it.octogram.android.ExperimentsPreferencesEntry";
        final String chats = "it.octogram.android.ChatsPreferencesEntry";

        put(c, Keys.HidePhone, NativeFeatureTarget.wrapperFragment(wrapper, privacy)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "hidePhoneNumber")
                .resKeys("HidePhoneNumber")
                .texts("Hide phone number")
                .hint("OctoGram > Privacy")
                .build());

        put(c, Keys.HideStoriesChatList, NativeFeatureTarget.wrapperFragment(wrapper, general)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "hideStories")
                .resKeys("HideStories")
                .texts("Hide stories")
                .hint("OctoGram > General")
                .build());

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.wrapperFragment(wrapper, experiments)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "downloadBoost")
                .resKeys("DownloadBoost", "DownloadBoostType")
                .texts("Download boost", "Download Boost type")
                .hint("OctoGram > Experiments")
                .build());

        put(c, Keys.UploadSpeed, NativeFeatureTarget.wrapperFragment(wrapper, experiments)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "uploadBoost")
                .resKeys("UploadBoost")
                .texts("Upload boost")
                .hint("OctoGram > Experiments")
                .build());

        put(c, Keys.ShowUserID, NativeFeatureTarget.wrapperFragment(wrapper, chats)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "showDcId")
                .resKeys("ShowDcID", "ShowRegistrationDate")
                .texts("Show Datacenter and ID", "Show registration date")
                .hint("OctoGram > Chats")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.wrapperFragment(wrapper, chats)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "formatTimeWithSeconds")
                .resKeys("FormatTimeWithSeconds")
                .texts("Format time with seconds")
                .hint("OctoGram > Chats")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.wrapperFragment(wrapper, chats)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "numberRounding")
                .resKeys("NumberRounding")
                .texts("Number rounding")
                .hint("OctoGram > Chats")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.wrapperFragment(wrapper, general)
                .nativeScroll(NativeScrollMode.OCTO_WRAPPER_KEY, "alwaysShowDownloads")
                .resKeys("AlwaysShowDownloads")
                .texts("Don't hide downloads")
                .hint("OctoGram > General")
                .build());
    }

    // =========================================================================
    // 4. Nagram (xyz.nextalone.nagram)
    // =========================================================================
    private static void registerNagram() {
        final ClientManager.Client c = ClientManager.Client.Nagram;
        final String ghost = "tw.nekomimi.nekogram.settings.GhostModeActivity";
        final String general = "tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity";
        final String chat = "tw.nekomimi.nekogram.settings.NekoChatSettingsActivity";
        final String experimental = "tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity";

        NativeFeatureTarget nagramHideSeen = NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendReadMessagePackets")
                .resKeys("DontReadMessages", "DontSendReadMessagePackets")
                .texts("Don't Read Messages", "Do Not Mark Messages as Read", "Don't read messages")
                .hint("Nagram > Ghost Mode")
                .build();
        putAll(c, nagramHideSeen, Keys.HideSeenPrivateChat, Keys.HideSeenChannel);

        put(c, Keys.MarkReadAfterSend, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "markReadAfterSend")
                .resKeys("MarkReadAfterSend")
                .texts("Mark as Read After Sending", "Read on Interact", "Mark Read After Send")
                .hint("Nagram > Ghost Mode")
                .build());

        put(c, Keys.HideTyping, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendUploadProgress")
                .resKeys("DontSendUploadProgress")
                .texts("Disable Typing Indicator", "Don't Send Typing")
                .hint("Nagram > Ghost Mode")
                .build());

        put(c, Keys.HideStoryView, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendReadStoryPackets")
                .resKeys("DontReadStories", "DontReadStoriesPackets")
                .texts("Do Not Mark Stories as Read", "Don't Read Stories")
                .hint("Nagram > Ghost Mode")
                .build());

        put(c, Keys.HideOnline, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendOnlinePackets")
                .resKeys("DontSendOnlinePackets")
                .texts("Disable Online Status", "Don't Send Online")
                .hint("Nagram > Ghost Mode")
                .build());

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "HidePhone")
                .resKeys("HidePhone")
                .texts("Hide my phone number")
                .hint("Nagram > General")
                .build());

        NativeFeatureTarget nagramStories = NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableStories")
                .resKeys("DisableStories")
                .texts("Disable Stories")
                .hint("Nagram > Experimental")
                .build();
        putAll(c, nagramStories,
                Keys.HideStoriesChatList,
                Keys.HideStoriesRings,
                Keys.HideStoriesProfile,
                Keys.DisableStoriesPosting);

        put(c, Keys.ShowMessageID, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ShowMessageID")
                .resKeys("ShowMessageID")
                .texts("Show message ID")
                .hint("Nagram > Chats")
                .build());

        NativeFeatureTarget nagramSpeed = NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "enhancedFileLoader")
                .resKeys("enhancedFileLoader")
                .texts("Enhance Upload and Download speed")
                .hint("Nagram > Experimental")
                .build();
        putAll(c, nagramSpeed, Keys.DownloadSpeed, Keys.UploadSpeed);

        put(c, Keys.HideNewChatButton, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableDialogsFloatingButton")
                .resKeys("DisableDialogsFloatingButton")
                .texts("Disable Dialogs Floating Button")
                .hint("Nagram > General")
                .build());

        put(c, Keys.HideProxySponsor, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "HideProxySponsorChannel")
                .resKeys("HideProxySponsorChannel")
                .texts("Hide proxy sponsor channel")
                .hint("Nagram > General")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "showSeconds")
                .resKeys("showSeconds")
                .texts("Show timestamp in seconds")
                .hint("Nagram > Chats")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableNumberRounding")
                .resKeys("DisableNumberRounding")
                .texts("Disable number rounding")
                .hint("Nagram > General")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "AlwaysShowDownloadIcon")
                .resKeys("AlwaysShowDownloadIcon")
                .texts("Always Show Download Icon")
                .hint("Nagram > General")
                .build());
    }

    // =========================================================================
    // 5. NagramX (nu.gpu.nagram)
    // =========================================================================
    private static void registerNagramX() {
        final ClientManager.Client c = ClientManager.Client.NagramX;
        final String ghost = "tw.nekomimi.nekogram.settings.GhostModeActivity";
        final String general = "tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity";
        final String chat = "tw.nekomimi.nekogram.settings.NekoChatSettingsActivity";
        final String experimental = "tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity";

        NativeFeatureTarget nxHideSeen = NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendReadMessagePackets")
                .resKeys("DontSendReadMessagePackets")
                .texts("Don't Read Messages")
                .hint("Nagram X > Ghost Mode")
                .build();
        putAll(c, nxHideSeen, Keys.HideSeenPrivateChat, Keys.HideSeenChannel);

        put(c, Keys.MarkReadAfterSend, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "markReadAfterSend")
                .resKeys("MarkReadAfterSend")
                .texts("Read on Interact")
                .hint("Nagram X > Ghost Mode")
                .build());

        put(c, Keys.HideTyping, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendUploadProgress")
                .resKeys("DontSendUploadProgress")
                .texts("Don't Send Typing")
                .hint("Nagram X > Ghost Mode")
                .build());

        put(c, Keys.HideStoryView, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendReadStoriesPackets")
                .resKeys("DontReadStoriesPackets")
                .texts("Don't Read Stories")
                .hint("Nagram X > Ghost Mode")
                .build());

        put(c, Keys.HideOnline, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "sendOnlinePackets")
                .resKeys("DontSendOnlinePackets", "SendOfflinePacketAfterOnline")
                .texts("Don't Send Online", "Go Offline Automatically")
                .hint("Nagram X > Ghost Mode")
                .build());

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "HidePhone")
                .resKeys("HidePhone")
                .texts("Hide Phone Number")
                .hint("Nagram X > General")
                .build());

        NativeFeatureTarget nxStories = NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableStories")
                .resKeys("DisableStories")
                .texts("Disable Stories")
                .hint("Nagram X > Experimental")
                .build();
        putAll(c, nxStories,
                Keys.HideStoriesChatList,
                Keys.HideStoriesRings,
                Keys.HideStoriesProfile,
                Keys.DisableStoriesPosting);

        NativeFeatureTarget nxDeleted = NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "EnableSaveDeletedMessages")
                .resKeys("EnableSaveDeletedMessages")
                .texts("Save Deleted Messages")
                .hint("Nagram X > Experimental")
                .build();
        putAll(c, nxDeleted,
                Keys.ShowDeletedPrivate,
                Keys.ShowDeletedGroups,
                Keys.ShowDeletedChannels);

        put(c, Keys.ClearDeletedMessages, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ClearMessageDatabase")
                .resKeys("ClearMessageDatabase")
                .texts("Clear Message Database")
                .hint("Nagram X > Experimental")
                .build());

        put(c, Keys.ShowMessageID, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ShowMessageID")
                .resKeys("ShowMessageID")
                .texts("Show Message ID")
                .hint("Nagram X > Chats")
                .build());

        put(c, Keys.SaveEditsHistory, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "EnableSaveEditsHistory")
                .resKeys("EnableSaveEditsHistory")
                .texts("Save Edits History")
                .hint("Nagram X > Experimental")
                .build());

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "enhancedFileLoader")
                .resKeys("enhancedFileLoader")
                .texts("Download Speed Boost")
                .hint("Nagram X > Experimental")
                .build());

        put(c, Keys.UploadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "uploadBoost")
                .resKeys("uploadBoost")
                .texts("Upload Speed Boost")
                .hint("Nagram X > Experimental")
                .build());

        put(c, Keys.HideNewChatButton, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableDialogsFloatingButton")
                .resKeys("DisableDialogsFloatingButton")
                .texts("Hide Floating Button")
                .hint("Nagram X > General")
                .build());

        put(c, Keys.ShowUserID, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ShowIdAndDc")
                .resKeys("ShowIdAndDc")
                .texts("Show ID / DC in Profile")
                .hint("Nagram X > General")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "showSeconds")
                .resKeys("showSeconds")
                .texts("Show Timestamp in Seconds")
                .hint("Nagram X > Chats")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableNumberRounding")
                .resKeys("DisableNumberRounding")
                .texts("Disable Number Rounding")
                .hint("Nagram X > General")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "AlwaysShowDownloadIcon")
                .resKeys("AlwaysShowDownloadIcon")
                .texts("Always Show Download Icon")
                .hint("Nagram X > General")
                .build());

        put(c, Keys.TelegramPremium, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "localPremium")
                .resKeys("localPremium")
                .texts("Local Premium")
                .hint("Nagram X > Experimental")
                .build());
    }

    // =========================================================================
    // 6. NagramXF (fork.risin42.nagramx)
    // =========================================================================
    private static void registerNagramXF() {
        final ClientManager.Client c = ClientManager.Client.NagramXF;
        final String ghost = "tw.nekomimi.nekogram.settings.GhostModeActivity";
        final String general = "tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity";
        final String chat = "tw.nekomimi.nekogram.settings.NekoChatSettingsActivity";
        final String experimental = "tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity";
        final String appearance = "tw.nekomimi.nekogram.settings.NekoAppearanceSettingsActivity";
        final String ayuSpy = "tw.nekomimi.nekogram.settings.NekoAyuSpySettingsActivity";
        final String ayuMoments = "tw.nekomimi.nekogram.settings.NekoAyuMomentsSettingsActivity";
        final String mainTabs = "tw.nekomimi.nekogram.settings.MainTabsCustomizeActivity";

        NativeFeatureTarget nxfHideSeen = NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "inv_sendReadMessagePackets")
                .resKeys("DontSendReadMessagePackets")
                .texts("Don't Read Messages")
                .hint("Nagram X Fork > Ghost Mode")
                .build();
        putAll(c, nxfHideSeen, Keys.HideSeenPrivateChat, Keys.HideSeenChannel);

        put(c, Keys.MarkReadAfterSend, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "MarkReadAfterSend")
                .resKeys("MarkReadAfterSend")
                .texts("Read on Interact")
                .hint("Nagram X Fork > Ghost Mode")
                .build());

        put(c, Keys.HideTyping, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "inv_sendUploadProgress")
                .resKeys("DontSendUploadProgress")
                .texts("Don't Send Typing")
                .hint("Nagram X Fork > Ghost Mode")
                .build());

        put(c, Keys.HideStoryView, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "inv_sendReadStoriesPackets")
                .resKeys("DontReadStoriesPackets")
                .texts("Don't Read Stories")
                .hint("Nagram X Fork > Ghost Mode")
                .build());

        put(c, Keys.HideOnline, NativeFeatureTarget.fragment(ghost)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "inv_sendOnlinePackets")
                .resKeys("DontSendOnlinePackets", "SendOfflinePacketAfterOnline")
                .texts("Don't Send Online", "Go Offline Automatically")
                .hint("Nagram X Fork > Ghost Mode")
                .build());

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "HidePhone")
                .resKeys("HidePhone")
                .texts("Hide Phone Number")
                .hint("Nagram X Fork > General")
                .build());

        NativeFeatureTarget nxfStories = NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableStories")
                .resKeys("DisableStories")
                .texts("Disable Stories")
                .hint("Nagram X Fork > Experimental")
                .build();
        putAll(c, nxfStories,
                Keys.HideStoriesChatList,
                Keys.HideStoriesRings,
                Keys.HideStoriesProfile,
                Keys.DisableStoriesPosting);

        NativeFeatureTarget nxfDeleted = NativeFeatureTarget.fragment(ayuSpy)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "EnableSaveDeletedMessages")
                .resKeys("EnableSaveDeletedMessages")
                .texts("Save Deleted Messages")
                .hint("Nagram X Fork > AyuSpy")
                .build();
        putAll(c, nxfDeleted,
                Keys.ShowDeletedPrivate,
                Keys.ShowDeletedGroups,
                Keys.ShowDeletedChannels);

        put(c, Keys.ClearDeletedMessages, NativeFeatureTarget.fragment(ayuSpy)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ClearSavedDataRow")
                .resKeys("ClearSavedMessageData")
                .texts("Clear", "Clear Saved Message Data")
                .hint("Nagram X Fork > AyuSpy")
                .build());

        put(c, Keys.SaveEditsHistory, NativeFeatureTarget.fragment(ayuSpy)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "EnableSaveEditsHistory")
                .resKeys("EnableSaveEditsHistory")
                .texts("Save Edits History")
                .hint("Nagram X Fork > AyuSpy")
                .build());

        put(c, Keys.ShowMessageID, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ShowMessageID")
                .resKeys("ShowMessageID")
                .texts("Show Message ID")
                .hint("Nagram X Fork > Chats")
                .build());

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "enhancedFileLoader")
                .resKeys("enhancedFileLoader")
                .texts("Download Speed Boost")
                .hint("Nagram X Fork > Experimental")
                .build());

        put(c, Keys.UploadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "uploadBoost")
                .resKeys("uploadBoost")
                .texts("Upload Speed Boost")
                .hint("Nagram X Fork > Experimental")
                .build());

        NativeFeatureTarget nxfTabs = NativeFeatureTarget.fragment(mainTabs)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "MainTabsDisplayMode")
                .resKeys("MainTabsDisplayMode", "MainTabsCustomize")
                .texts("Bottom Bar Display Mode", "Customize Bottom Tabs")
                .hint("Nagram X Fork > Customize Bottom Tabs")
                .build();
        putAll(c, nxfTabs,
                Keys.HideBottomBarTabs,
                Keys.HideBottomTabContacts,
                Keys.HideBottomTabSettingsCalls,
                Keys.HideBottomTabProfile);

        put(c, Keys.HideNewChatButton, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableDialogsFloatingButton")
                .resKeys("DisableDialogsFloatingButton")
                .texts("Hide Floating Button")
                .hint("Nagram X Fork > Appearance")
                .build());

        put(c, Keys.HideSponsoredAds, NativeFeatureTarget.fragment(ayuMoments)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableAds")
                .resKeys("DisableAds")
                .texts("Disable ads")
                .hint("Nagram X Fork > AyuMoments")
                .build());

        put(c, Keys.ShowUserID, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "ShowIdAndDc")
                .resKeys("ShowIdAndDc")
                .texts("Show ID / DC in Profile")
                .hint("Nagram X Fork > General")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "showSeconds")
                .resKeys("showSeconds")
                .texts("Show Timestamp in Seconds")
                .hint("Nagram X Fork > Chats")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "DisableNumberRounding")
                .resKeys("DisableNumberRounding")
                .texts("Disable Number Rounding")
                .hint("Nagram X Fork > General")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "AlwaysShowDownloadIcon")
                .resKeys("AlwaysShowDownloadIcon")
                .texts("Always Show Download Icon")
                .hint("Nagram X Fork > Appearance")
                .build());

        put(c, Keys.TelegramPremium, NativeFeatureTarget.fragment(ayuMoments)
                .nativeScroll(NativeScrollMode.NEKO_SCROLL_TO_ROW, "localPremium")
                .resKeys("localPremium")
                .texts("Local Premium")
                .hint("Nagram X Fork > AyuMoments")
                .build());
    }

    // =========================================================================
    // 7. Nekogram (tw.nekomimi.nekogram)
    // =========================================================================
    private static void registerNekogram() {
        final ClientManager.Client c = ClientManager.Client.Nekogram;
        final String appearance = "tw.nekomimi.nekogram.settings.NekoAppearanceSettingsActivity";
        final String experimental = "tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity";

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW, "formatTimeWithSeconds")
                .resKeys("FormatTimeWithSeconds")
                .texts("Format time with seconds")
                .hint("Nekogram > Appearance")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW, "disableNumberRounding")
                .resKeys("DisableNumberRounding")
                .texts("Disable number rounding")
                .hint("Nekogram > Appearance")
                .build());

        put(c, Keys.HideStoriesChatList, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW, "hideStories")
                .resKeys("HideStories")
                .texts("Hide Stories")
                .hint("Nekogram > Appearance")
                .build());

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW, "downloadSpeedBoost")
                .resKeys("DownloadSpeedBoost")
                .texts("Download speed boost")
                .hint("Nekogram > Experimental")
                .build());

        put(c, Keys.RemovesContentSaving, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW, "contentRestriction")
                .resKeys("IgnoreContentRestriction")
                .texts("Ignore content restriction")
                .hint("Nekogram > Experimental")
                .build());
    }

    // =========================================================================
    // 8. Momogram (momo.gram)
    // =========================================================================
    private static void registerMomogram() {
        final ClientManager.Client c = ClientManager.Client.Momogram;
        final String chat = "moe.hx030.momogram.settings.MomoChatSettingsActivity";
        final String appearance = "moe.hx030.momogram.settings.MomoAppearanceSettingsActivity";
        final String general = "moe.hx030.momogram.settings.MomoGeneralSettingsActivity";
        final String experimental = "moe.hx030.momogram.settings.MomoExperimentalSettingsActivity";
        final String nekox = "moe.hx030.momogram.settings.NekoXSettingActivity";

        put(c, Keys.HideTyping, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "disableChatAction")
                .rowFields("disableChatActionRow")
                .texts("Don't send my input status")
                .hint("Momogram > Chats")
                .build());

        put(c, Keys.HideStoryView, NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "disableSendReadStories")
                .rowFields("disableSendReadStoriesRow")
                .texts("Disable Send Read Stories")
                .hint("Momogram > Chats")
                .build());

        NativeFeatureTarget momoStories = NativeFeatureTarget.fragment(chat)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "disableStories")
                .rowFields("disableStoriesRow")
                .texts("Disable Stories")
                .hint("Momogram > Chats")
                .build();
        putAll(c, momoStories, Keys.HideStoriesChatList, Keys.HideStoriesRings);

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "hidePhone")
                .rowFields("hidePhoneRow")
                .texts("Hide my phone number")
                .hint("Momogram > Appearance")
                .build());

        put(c, Keys.HideNewChatButton, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "hideFAB")
                .rowFields("hideFABRow")
                .texts("Hide floating buttons in chat list")
                .hint("Momogram > Appearance")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "showSeconds")
                .rowFields("showSeconds")
                .texts("Show timestamp in seconds")
                .hint("Momogram > Appearance")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(appearance)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "disableNumberRounding")
                .rowFields("disableNumberRoundingRow")
                .texts("Disable number rounding")
                .hint("Momogram > Appearance")
                .build());

        put(c, Keys.HideProxySponsor, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "hideProxySponsorChannel")
                .rowFields("hideProxySponsorChannelRow")
                .texts("Hide proxy sponsor channel")
                .hint("Momogram > General")
                .build());

        put(c, Keys.HideSponsoredAds, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "hideSponsoredMessage")
                .rowFields("hideSponsoredMessageRow")
                .texts("Hide sponsored messages")
                .hint("Momogram > General")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "alwaysShowDownloads")
                .rowFields("alwaysShowDownloadsRow")
                .texts("Always show downloads button")
                .hint("Momogram > General")
                .build());

        put(c, Keys.AlwaysShowProxy, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "hideProxyByDefault")
                .rowFields("hideProxyByDefaultRow")
                .texts("Hide proxy settings by default")
                .hint("Momogram > General")
                .build());

        put(c, Keys.HideUpdateApp, NativeFeatureTarget.fragment(general)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "checkUpdate")
                .rowFields("checkUpdateRow")
                .texts("Check for updates automatically")
                .hint("Momogram > General")
                .build());

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(experimental)
                .nativeScroll(NativeScrollMode.MOMO_SET_SCROLL_TO, "enhancedFileLoader")
                .rowFields("enhancedFileLoaderRow")
                .resKeys("enhancedFileLoader")
                .texts("Enhance Download speed")
                .hint("Momogram > Experimental")
                .build());

        put(c, Keys.HideOnline, NativeFeatureTarget.fragment(nekox)
                .rowFields("disableStatusUpdateRow")
                .texts("Disable Status Update")
                .hint("Momogram > NekoX Settings")
                .build());
    }

    // =========================================================================
    // 9. Forkgram (forkgram, forkgramBeta, ForkgramClassic)
    // =========================================================================
    private static void registerForkgramFamily() {
        final String forkSettings = "org.forkgram.messenger.ForkSettingsActivity";
        Map<String, NativeFeatureTarget> map = new HashMap<>();

        map.put(Keys.HidePhone, NativeFeatureTarget.fragment(forkSettings)
                .texts("Hide Phone Number", "Hide Sensitive Data")
                .hint("Forkgram > Fork Settings")
                .build());

        map.put(Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(forkSettings)
                .texts("Format Message Time with Seconds")
                .hint("Forkgram > Fork Settings")
                .build());

        map.put(Keys.HideUpdateApp, NativeFeatureTarget.fragment(forkSettings)
                .texts("Update Check Interval")
                .hint("Forkgram > Fork Settings")
                .build());

        registerShared(map,
                ClientManager.Client.forkgram,
                ClientManager.Client.forkgramBeta,
                ClientManager.Client.ForkgramClassic);
    }

    // =========================================================================
    // 10. Telegraph (ir.ilmili.telegraph)
    // =========================================================================
    private static void registerTelegraph() {
        final ClientManager.Client c = ClientManager.Client.Telegraph;
        final String general = "org.telegram.ui.TelegraphSettingsGeneralActivity";
        final String dialogs = "org.telegram.ui.TelegraphSettingsDialogsActivity";
        final String chat = "org.telegram.ui.TelegraphSettingsChatActivity";

        put(c, Keys.DownloadSpeed, NativeFeatureTarget.fragment(general)
                .rowFields("boostDownloadSpeedSelectorRow", "boostDownloadSpeedHeaderRow")
                .texts("Boost Download Speed")
                .hint("Telegraph > General")
                .build());

        put(c, Keys.UploadSpeed, NativeFeatureTarget.fragment(general)
                .rowFields("boostUploadSpeedRow")
                .texts("Boost Upload Speed")
                .hint("Telegraph > General")
                .build());

        put(c, Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(general)
                .rowFields("formatTimeWithSecondsRow")
                .texts("Format time with seconds")
                .hint("Telegraph > General")
                .build());

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(general)
                .rowFields("scratchNumberRow")
                .texts("Scratch phone number")
                .hint("Telegraph > General")
                .build());

        put(c, Keys.AlwaysShowProxy, NativeFeatureTarget.fragment(dialogs)
                .rowFields("mainPageIconsRow")
                .texts("Main page icons")
                .hint("Telegraph > Dialogs")
                .build());

        put(c, Keys.AlwaysShowDownloads, NativeFeatureTarget.fragment(dialogs)
                .rowFields("activeDownloadsButtonRow", "mainPageIconsRow")
                .texts("Show active downloads button")
                .hint("Telegraph > Dialogs")
                .build());

        put(c, Keys.HideStoriesChatList, NativeFeatureTarget.fragment(dialogs)
                .rowFields("showStoriesRow")
                .texts("Show Stories", "Show stories in the title bar")
                .hint("Telegraph > Dialogs")
                .build());

        put(c, Keys.DisableNumberRounding, NativeFeatureTarget.fragment(chat)
                .rowFields("showShortMemberRow")
                .texts("Rounding members count")
                .hint("Telegraph > Chats")
                .build());
    }

    // =========================================================================
    // 11. iMe (iMe, iMeWeb)
    // =========================================================================
    private static void registerIMeFamily() {
        final String messages = "com.iMe.fork.ui.fragment.MessagesSettingsFragment";
        final String mainTabs = "com.iMe.fork.ui.fragment.MainTabsButtonsSettingsFragment";
        final String threeDots = "com.iMe.fork.ui.fragment.ThreeDotsMenuSettingsFragment";
        Map<String, NativeFeatureTarget> map = new HashMap<>();

        map.put(Keys.ShowTimeSeconds, NativeFeatureTarget.fragment(messages)
                .resKeys("settings_show_seconds")
                .texts("Seconds in message time")
                .hint("iMe > Tools > Messages")
                .build());

        map.put(Keys.HideBottomBarTabs, NativeFeatureTarget.fragment(mainTabs)
                .resKeys("settings_main_tabs_show", "settings_main_tabs_buttons_header")
                .texts("Show tab bar", "Tab bar buttons")
                .hint("iMe > Tools > Tab bar buttons")
                .build());

        map.put(Keys.HideBottomTabContacts, NativeFeatureTarget.fragment(mainTabs)
                .resKeys("settings_main_tabs_buttons_header")
                .texts("Contacts", "Tab bar buttons")
                .hint("iMe > Tools > Tab bar buttons")
                .build());

        map.put(Keys.HideBottomTabSettingsCalls, NativeFeatureTarget.fragment(mainTabs)
                .resKeys("settings_main_tabs_buttons_header")
                .texts("Calls", "Settings", "Tab bar buttons")
                .hint("iMe > Tools > Tab bar buttons")
                .build());

        map.put(Keys.HideBottomTabProfile, NativeFeatureTarget.fragment(mainTabs)
                .resKeys("settings_main_tabs_show")
                .texts("Show tab bar")
                .hint("iMe > Tools > Tab bar buttons")
                .build());

        map.put(Keys.AlwaysShowProxy, NativeFeatureTarget.fragment(threeDots)
                .resKeys("settings_proxy_button_in_toolbar")
                .texts("Proxy button in 3 dots")
                .hint("iMe > Tools > Three dots menu")
                .build());

        registerShared(map, ClientManager.Client.iMe, ClientManager.Client.iMeWeb);
    }

    // =========================================================================
    // 12. Mercurygram (it.belloworld.mercurygram)
    // =========================================================================
    private static void registerMercurygram() {
        final ClientManager.Client c = ClientManager.Client.Mercurygram;
        final String mgSettings = "it.belloworld.mercurygram.ui.MercurygramSettingsActivity";
        final String debugSettings = "it.belloworld.mercurygram.DebugSettingsActivity";

        put(c, Keys.HideStoriesChatList, NativeFeatureTarget.fragment(mgSettings)
                .rowFields("ID_HIDE_STORIES")
                .texts("Hide stories")
                .hint("Mercurygram > Mercurygram Settings")
                .build());

        NativeFeatureTarget mgSavedHistory = NativeFeatureTarget.fragment(mgSettings)
                .rowFields("ID_SAVED_MESSAGES_HISTORY")
                .texts("Save deleted & edited messages")
                .hint("Mercurygram > Mercurygram Settings")
                .build();
        putAll(c, mgSavedHistory,
                Keys.ShowDeletedPrivate,
                Keys.ShowDeletedGroups,
                Keys.ShowDeletedChannels,
                Keys.SaveEditsHistory);

        put(c, Keys.HideUpdateApp, NativeFeatureTarget.fragment(mgSettings)
                .rowFields("ID_DISABLE_AUTO_UPDATE")
                .texts("Disable in-app updater")
                .hint("Mercurygram > Mercurygram Settings")
                .build());

        NativeFeatureTarget mgRemoveAds = NativeFeatureTarget.fragment(debugSettings)
                .texts("Remove Ads & Proxy Sponsor")
                .hint("Mercurygram > Debug Settings")
                .build();
        putAll(c, mgRemoveAds, Keys.HideSponsoredAds, Keys.HideProxySponsor);
    }

    // =========================================================================
    // 13. Nicegram (app.nicegram)
    // =========================================================================
    private static void registerNicegram() {
        final ClientManager.Client c = ClientManager.Client.Nicegram;
        final String ngSettings = "app.nicegram.NicegramSettingsActivity";

        put(c, Keys.HidePhone, NativeFeatureTarget.fragment(ngSettings)
                .resKeys("HidePhoneNumber")
                .texts("Hide phone in settings")
                .hint("Nicegram > Nicegram Settings")
                .build());
    }
}
