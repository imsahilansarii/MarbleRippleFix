package com.sahil.ripplefix;

import android.graphics.Point;
import android.view.View;
import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static Object sRippleController = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("MarbleRippleFix: Loaded into SystemUI!");

        Class<?> rippleCtrlClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.AuthRippleController", lpparam.classLoader);

        if (rippleCtrlClass == null) {
            XposedBridge.log("MarbleRippleFix: AuthRippleController class NOT found!");
            return;
        }

        // Log all method names in AuthRippleController for debugging
        for (Method m : rippleCtrlClass.getDeclaredMethods()) {
            XposedBridge.log("MarbleRippleFix Method: " + m.getName());
        }

        // Capture AuthRippleController instance on creation/init
        XposedBridge.hookAllConstructors(rippleCtrlClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                sRippleController = param.thisObject;
                XposedBridge.log("MarbleRippleFix: Captured AuthRippleController instance!");
            }
        });

        // Helper runnable to force ripple directly on AuthRippleView
        final Runnable forceRipple = new Runnable() {
            @Override
            public void run() {
                if (sRippleController == null) {
                    XposedBridge.log("MarbleRippleFix: sRippleController is null!");
                    return;
                }
                try {
                    // 1. Set sensor coordinates on controller
                    try {
                        XposedHelpers.setObjectField(sRippleController, "fingerprintSensorLocation", new Point(1080, 1025));
                    } catch (Throwable ignored) {}

                    // 2. Get mView (AuthRippleView) from controller
                    View rippleView = (View) XposedHelpers.getObjectField(sRippleController, "mView");
                    if (rippleView != null) {
                        rippleView.post(() -> {
                            try {
                                rippleView.setVisibility(View.VISIBLE);
                                if (rippleView.getWidth() == 0 || rippleView.getHeight() == 0) {
                                    rippleView.layout(0, 0, 1080, 2400);
                                }
                                try {
                                    XposedHelpers.callMethod(rippleView, "setFingerprintSensorLocation", new Point(1080, 1025), 0f);
                                } catch (Throwable e1) {
                                    try {
                                        XposedHelpers.callMethod(rippleView, "setFingerprintSensorLocation", new Point(1080, 1025));
                                    } catch (Throwable ignored) {}
                                }
                                XposedHelpers.callMethod(rippleView, "startUnlockedRipple");
                                XposedBridge.log("MarbleRippleFix: Direct mView.startUnlockedRipple() FIRED!");
                            } catch (Throwable t) {
                                XposedBridge.log("MarbleRippleFix View Error: " + t.getMessage());
                            }
                        });
                    }

                    // 3. Also call controller's showUnlockRipple as fallback
                    Class<?> bioTypeClass = XposedHelpers.findClass(
                            "android.hardware.biometrics.BiometricSourceType", lpparam.classLoader);
                    Object fpType = Enum.valueOf((Class<Enum>) bioTypeClass, "FINGERPRINT");
                    XposedHelpers.callMethod(sRippleController, "showUnlockRipple", fpType);
                } catch (Throwable t) {
                    XposedBridge.log("MarbleRippleFix Trigger Error: " + t.getMessage());
                }
            }
        };

        // Hook BiometricUnlockController (Always called on FP wake/unlock)
        Class<?> bioUnlockClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.BiometricUnlockController", lpparam.classLoader);
        if (bioUnlockClass != null) {
            XposedBridge.hookAllMethods(bioUnlockClass, "onBiometricAuthenticated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("MarbleRippleFix: BiometricUnlockController.onBiometricAuthenticated called!");
                    forceRipple.run();
                }
            });
        }

        // Also hook KeyguardUpdateMonitor just in case
        Class<?> kumClass = XposedHelpers.findClassIfExists(
                "com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader);
        if (kumClass != null) {
            XposedBridge.hookAllMethods(kumClass, "onFingerprintAuthenticated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("MarbleRippleFix: KeyguardUpdateMonitor.onFingerprintAuthenticated called!");
                    forceRipple.run();
                }
            });
        }
    }
}
