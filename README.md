# 🌊 Marble Ripple Fix (v1.1)

> **📌 Note:** I created this module as a **temporary fix** specifically for **Evolution X 12.3** on **POCO F5 / Redmi Note 12 Turbo (`marble`)**, where the biometric unlock ripple animation was broken after the ROM switched to the Stock Xiaomi Fingerprint HAL.

---

## 🔍 Root Cause in Evolution X 12.3
1. `AuthRippleView` remains stuck in `View.GONE` state with `0,0-0,0` layout bounds.
2. The Stock FP HAL wake-and-unlock path bypasses SystemUI's standard `AuthRippleController` trigger, even though sensor coordinates (`Point(1080, 1025)`) are present.

## ✨ What's New in v1.1
- **Zero-Lag Reflection Caching:** Pre-caches `AuthRippleController` methods & `BiometricSourceType` at SystemUI startup instead of scanning on every unlock.
- **Dynamic DisplayMetrics Bounds:** Automatically reads screen width/height instead of relying solely on hardcoded dimensions.
- **450ms Debounce Guard:** Prevents duplicate ripple triggers.

## 📦 Installation
1. Download and install **`MarbleRippleFix-v1.1.apk`** from [Releases](../../releases).
2. Enable the module in **Vector / LSPosed** (Scope: `System UI`).
3. Restart SystemUI (`su -c "killall com.android.systemui"`).

## 👤 Author
- **Sahil Ansari** — [@imsahilansarii](https://t.me/imsahilansarii)
