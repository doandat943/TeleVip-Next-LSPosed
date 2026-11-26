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
  *(Hidden automatically on clients with built-in speed boosters such as Telegraph)*

### 📸 Media & Stories
- **Disable Stories**
- **Save Secret / Self-Destructing Media**
- **Prevent Media Deletion**
- **Save Protected Stories** to gallery
- **Save Voice Messages** (Voice-to-Music saving)

### 🎨 UI & Customization
- **Hide Pinned Messages** banner
- **Disable Channel Swipe Back** & **Disable Profile Swipe Back**
- **Hide Proxy Sponsor** channel
- **Always Show Proxy** button (`Off` / `In Menu` / `Top Bar`)
- **Show Time Seconds** in chat & profile timestamps
- **Show User ID** in profiles
- **Disable Number Rounding** (`5.3K` → `5300`)
- **Custom Calendar** (Gregorian / Hijri / Jalali)
- **In-App Language Picker & RTL Support** (Auto, English, Tiếng Việt, العربية, فارسی, Español, 中文)

### 🛠️ Other Features
- **Remove Content Saving Restrictions** (`noforwards`)
- **Copy Attribution** (toggle where copied messages came from)
- **Local Telegram Premium** unlock
- **Hide App Update Prompts**
- **Fix TL Scheme Parse Errors**

---

## 📱 Supported Clients

| Official & Base | Third-Party Clients |
|---|---|
| Telegram (`org.telegram.messenger`) | Nagram (`xyz.nextalone.nagram`) |
| Telegram Beta (`org.telegram.messenger.beta`) | NagramX (`nu.gpu.nagram`) |
| Telegram Web (`org.telegram.messenger.web`) | Nagram XF (`fork.risin42.nagramx`) |
| Plus Messenger (`org.telegram.plus`) | Nekogram (`tw.nekomimi.nekogram`) |
| TG Connect (`com.tgconnect.android`) | Cherrygram (`uz.unnarsx.cherrygram`) |
| iMe (`com.iMe.android`) | Nicegram (`app.nicegram`) |
| iMe Web (`com.iMe.android.web`) | Telegraph (`ir.ilmili.telegraph`) |
| Forkgram (`org.forkgram.messenger`) | Telega (`ru.dahl.messenger`) |
| ForkClient Beta (`org.forkclient.messenger.beta`) | Momogram (`nekox.messenger.broken`) |
| Forkgram Classic (`org.forkgram.classic`) | Turrit (`org.telegram.group`) |
| X Plus (`com.xplus.messenger`) | |

> Note: Thanks to runtime DEX `AutoMappingResolver`, official Telegram, Telegraph, and Cherrygram updates adapt automatically without waiting for hardcoded mapping updates.

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
