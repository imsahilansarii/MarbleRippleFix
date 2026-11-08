package com.sahil.ripplefix;

import android.graphics.Point;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static Object sRippleController = null;
    private static long sLastRippleTime = 0;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        Class<?> rippleCtrlClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.AuthRippleController", lpparam.classLoader);

        if (rippleCtrlClass == null) return;

        // Capture AuthRippleController instance & log its fields
        XposedBridge.hookAllConstructors(rippleCtrlClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                sRippleController = param.thisObject;
                XposedBridge.log("MarbleRippleFix: Captured AuthRippleController!");
            }
        });

        // Also ensure whenever showUnlockedRipple runs, sensor coordinates & view bounds are valid
        XposedBridge.hookAllMethods(rippleCtrlClass, "showUnlockedRipple", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object ctrl = param.thisObject;
                try {
                    XposedHelpers.setObjectField(ctrl, "fingerprintSensorLocation", new Point(1080, 1025));
                } catch (Throwable ignored) {}

                // Find the AuthRippleView field inside controller (even if obfuscated or named mView)
                for (Field f : ctrl.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(ctrl);
                    if (val instanceof View) {
                        View v = (View) val;
                        v.setVisibility(View.VISIBLE);
                        if (v.getWidth() == 0 || v.getHeight() == 0) {
                            v.layout(0, 0, 1080, 2400);
                        }
                        try {
                            XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025), 0f);
                        } catch (Throwable e1) {
                            try {
                                XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025));
                            } catch (Throwable ignored) {}
                        }
                    }
                }
                // Check superclass fields too (ViewController.mView)
                try {
                    Field mViewField = XposedHelpers.findFieldIfExists(ctrl.getClass().getSuperclass(), "mView");
                    if (mViewField != null) {
                        mViewField.setAccessible(true);
                        View v = (View) mViewField.get(ctrl);
                        if (v != null) {
                            v.setVisibility(View.VISIBLE);
                            if (v.getWidth() == 0 || v.getHeight() == 0) {
                                v.layout(0, 0, 1080, 2400);
                            }
                            try {
                                XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025), 0f);
                            } catch (Throwable e1) {
                                try {
                                    XposedHelpers.callMethod(v, "setFingerprintSensorLocation", new Point(1080, 1025));
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });

        // Trigger function with 500ms debounce so it doesn't fire twice at the same millisecond
        final Runnable triggerRipple = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                if (now - sLastRippleTime < 500) return;
                sLastRippleTime = now;

                if (sRippleController == null) return;

                try {
                    // 1. Try access$showUnlockRippleInternal(AuthRippleController, BiometricSourceType)
                    Class<?> bioTypeClass = XposedHelpers.findClass(
                            "android.hardware.biometrics.BiometricSourceType", lpparam.classLoader);
                    Object fpType = Enum.valueOf((Class<Enum>) bioTypeClass, "FINGERPRINT");

                    boolean triggered = false;
                    for (Method m : sRippleController.getClass().getDeclaredMethods()) {
                        if (m.getName().equals("access$showUnlockRippleInternal")) {
                            m.setAccessible(true);
                            if (m.getParameterCount() == 2) {
                                m.invoke(null, sRippleController, fpType);
                                triggered = true;
                                XposedBridge.log("MarbleRippleFix: Fired access$showUnlockRippleInternal(ctrl, FP)!");
                            } else if (m.getParameterCount() == 1) {
                                m.invoke(sRippleController, fpType);
                                triggered = true;
                                XposedBridge.log("MarbleRippleFix: Fired access$showUnlockRippleInternal(FP)!");
                            }
                        }
                    }

                    // 2. Also call showUnlockedRipple() directly
                    if (!triggered) {
                        for (Method m : sRippleController.getClass().getDeclaredMethods()) {
                            if (m.getName().equals("showUnlockedRipple")) {
                                m.setAccessible(true);
                                if (m.getParameterCount() == 0) {
                                    m.invoke(sRippleController);
                                    XposedBridge.log("MarbleRippleFix: Fired showUnlockedRipple()!");
                                } else if (m.getParameterCount() == 1) {
                                    m.invoke(sRippleController, fpType);
                                    XposedBridge.log("MarbleRippleFix: Fired showUnlockedRipple(FP)!");
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log("MarbleRippleFix Error: " + t.toString());
                }
            }
        };

        // Hook KeyguardUpdateMonitor.onFingerprintAuthenticated
        Class<?> kumClass = XposedHelpers.findClassIfExists(
                "com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader);
        if (kumClass != null) {
            XposedBridge.hookAllMethods(kumClass, "onFingerprintAuthenticated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    triggerRipple.run();
                }
            });
        }
    }
}
