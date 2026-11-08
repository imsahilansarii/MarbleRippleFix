# 🌊 Marble Ripple Fix

Lightweight Xposed/Vector module to restore the Biometric Unlock Ripple Animation on POCO F5 (marble) running AOSP ROMs with Stock Xiaomi FP HAL.

## ⚡ Features
- Restores power button unlock ripple at Point(1080, 1025)
- Fixes AuthRippleView 0x0 bounds and View.GONE issue
- 500ms debounce guard to prevent double-firing
- Zero background services or battery drain

## 📦 Installation
1. Install the APK from Releases.
2. Enable in Vector / LSPosed (Scope: System UI).
3. Restart SystemUI.

## 👤 Author
- **Sahil Ansari** ([@imsahilansarii](https://t.me/imsahilansarii))
