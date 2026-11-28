# TeleVip Next

<p>
  <img src="https://img.shields.io/badge/Platform-Android%208.1%2B-green" alt="Android 8.1+">
  <img src="https://img.shields.io/badge/Framework-Modern%20Xposed%20(API%20101--102)%20%7C%20LSPosed%2093%2B-blue" alt="Modern Xposed & LSPosed">
  <img src="https://img.shields.io/badge/Package-com.magiii.televipnext-blueviolet" alt="Package">
  <img src="https://img.shields.io/badge/License-GPL--3.0-orange" alt="GPL-3.0">
</p>

A dual-API Xposed module (native **libxposed API 101–102** with **Legacy API 93+** fallback) that brings privacy controls, deleted-message & edit-history recovery, media protection bypasses, speed boosts, and UI customization to official and third-party Telegram clients — featuring an automatic DEX `AutoMappingResolver` for obfuscated clients.

---

## ✨ Features

### 🔒 Privacy & Ghost Mode
- **Hide "Seen" Status** independently for Private Chats and Channels/Groups
- **Mark Read After Send**
- **Hide "Typing..." Indicator**
- **Hide Story View Status**
- **Hide Phone Number** in drawer & profile UI
- **Hide Online Status** (offline visibility)

### 💬 Messages
- **Show Deleted Messages** with per-chat-type filtering (Private / Groups / Channels), local single-message delete menu, and bulk clear
- **Show Message ID** alongside timestamps
- **Save Message Edit History** locally with in-menu history viewer

### ⚡ Connections & Speed
- **Download Speed Boost** (`Off` / `Fast` / `Ultra`)
- **Upload Speed Boost**
  *(Features already natively built into your client are automatically shown disabled; double-tap them in TeleVip Next settings to jump directly to the client's native option)*

### 📸 Media & Stories
- **Disable Stories**
- **Save Secret / Self-Destructing Media**
- **Prevent Media Deletion**
- **Save Protected Stories** to gallery
- **Save Voice Messages** (Voice-to-Music saving)

### 🎨 UI & Customization
- **Hide Pinned Messages** banner
- **Hide Bottom Bar Tabs** (Contacts, Calls/Settings, Profile)
- **Hide New Chat Button (+)**
- **Hide Sponsored Ads**
- **Disable Channel Swipe Back** & **Disable Profile Swipe Back**
- **Hide Proxy Sponsor** channel
- **Always Show Proxy** button (`Off` / `In Menu` / `Top Bar`)
- **Always Show Downloads** button in main toolbar
- **Show Time Seconds** in chat & profile timestamps
- **Show ID & DC in Profile** (with estimated account creation / chat join date)
- **Disable Number Rounding** (`5.3K` → `5300`)
- **Custom Calendar** (Gregorian / Hijri / Jalali)
- **In-App Language Picker & RTL Support** — 42 languages (`Auto`, English, Bahasa Indonesia, Русский, Tiếng Việt, العربية, فارسی, עברית, اردو, Español, Français, Deutsch, Italiano, Português, Türkçe, Українська, 日本語, 한국어, 中文, ไทย, हिन्दी, and more)

### 🛠️ Other Features
- **Remove Content Saving Restrictions** (`noforwards`)
- **Copy Attribution** (toggle where copied messages came from)
- **Local Telegram Premium** unlock
- **Hide App Update Prompts**
- **Fix TL Scheme Parse Errors**

---

## 📱 Supported Clients (19 Active)

| Official & Base | Third-Party Clients |
|---|---|
| Telegram (`org.telegram.messenger`) | Mercurygram (`it.belloworld.mercurygram`) |
| Telegram Beta (`org.telegram.messenger.beta`) | Nagram (`xyz.nextalone.nagram`) |
| Telegram Web (`org.telegram.messenger.web`) | Nagram XF (`fork.risin42.nagramx`) |
| Plus Messenger (`org.telegram.plus`) | Nekogram (`tw.nekomimi.nekogram`) |
| iMe (`com.iMe.android`) | Cherrygram (`uz.unnarsx.cherrygram`) |
| iMe Web (`com.iMe.android.web`) | Nicegram (`app.nicegram`) |
| Forkgram (`org.forkgram.messenger`) | Telegraph (`ir.ilmili.telegraph`) |
| ForkClient Beta (`org.forkclient.messenger.beta`) | Momogram (`momo.gram`) |
| Forkgram Classic (`org.forkgram.classic`) | Turrit (`org.telegram.group`) |
| X Plus (`com.xplus.messenger`) | |

> Note: Runtime DEX `AutoMappingResolver` supports fully obfuscated, partially obfuscated, root-repackaged (`tgnet`-obfuscated), and legacy drawer-UI builds automatically — keeping official Telegram, Plus Messenger, Mercurygram, Nagram, Nekogram, Cherrygram, Telegraph, Momogram, Forkgram, Forkgram Classic, Nicegram, iMe, X Plus, and Turrit working across updates without hardcoded JSON mapping tables.

---

## 📦 Installation

1. Install **LSPosed / Vector** (Modern Xposed API 101–102 or Legacy API 93+).
2. Install **TeleVip Next** (`com.magiii.televipnext`).
   - *If you previously used legacy `com.my.televip`, uninstall it first to avoid duplicate hooks.*
3. Open **LSPosed Manager** → **Modules** → enable **TeleVip Next**.
4. Select your Telegram client(s) in the module scope and force-stop/relaunch Telegram.
5. Open Telegram **Settings** to access **TeleVip Next** settings.

---

## 📢 Community & Updates

- Telegram Channel: https://t.me/televipnext
- Source Code: https://github.com/doandat943/TeleVip-Next-LSPosed

---

## ⚠️ Disclaimer

This module is provided for educational purposes only. Modifying client behavior may violate Telegram's Terms of Service. Use at your own risk.

---

## 📄 License

Licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](./LICENSE).

## 🙏 Credits

- Partially based on [Re-Telegram](https://github.com/Sakion-Team/Re-Telegram).
- Originally developed by [**@mustafa1dev**](https://github.com/mustafa1dev), continued & maintained by [**@doandat943**](https://github.com/doandat943) (DANG DOAN DAT).
