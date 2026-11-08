package com.sahil.ripplefix;

import android.graphics.Point;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        // 1. Ensure AuthRippleView is VISIBLE and has full screen bounds (fixes 0,0-0,0)
        Class<?> rippleViewClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.AuthRippleView", lpparam.classLoader);

        if (rippleViewClass != null) {
            XposedBridge.hookAllMethods(rippleViewClass, "startUnlockedRipple", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setVisibility(View.VISIBLE);
                    if (v.getWidth() == 0 || v.getHeight() == 0) {
                        v.layout(0, 0, 1080, 2400);
                    }
                    try {
                        XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025), 0f);
                    } catch (Throwable ignored) {
                        try {
                            XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025));
                        } catch (Throwable ignored2) {}
                    }
                }
            });
        }

        // 2. Force trigger showUnlockRipple when Stock FP HAL unlocks or fades keyguard
        Class<?> rippleCtrlClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.AuthRippleController", lpparam.classLoader);

        if (rippleCtrlClass != null) {
            XC_MethodHook triggerHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        XposedHelpers.setObjectField(param.thisObject, "fingerprintSensorLocation", new Point(1080, 1025));
                    } catch (Throwable ignored) {}
                    try {
                        Class<?> bioTypeClass = XposedHelpers.findClass(
                                "android.hardware.biometrics.BiometricSourceType", lpparam.classLoader);
                        Object fpType = Enum.valueOf((Class<Enum>) bioTypeClass, "FINGERPRINT");
                        XposedHelpers.callMethod(param.thisObject, "showUnlockRipple", fpType);
                    } catch (Throwable ignored) {
                        try {
                            XposedHelpers.callMethod(param.thisObject, "showUnlockedRipple");
                        } catch (Throwable ignored2) {}
                    }
                }
            };

            XposedBridge.hookAllMethods(rippleCtrlClass, "onKeyguardFadingAwayChanged", triggerHook);
            XposedBridge.hookAllMethods(rippleCtrlClass, "onBiometricAuthenticated", triggerHook);
        }
    }
}
