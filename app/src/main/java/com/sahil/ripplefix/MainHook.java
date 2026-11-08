package com.sahil.ripplefix;

import android.graphics.Point;
import android.util.DisplayMetrics;
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
    private static Method sInternalMethod = null;
    private static Method sPublicMethod = null;
    private static Object sFpSourceType = null;
    private static long sLastRippleTime = 0;
    private static final Point SENSOR_POINT = new Point(1080, 1025);

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        Class<?> rippleCtrlClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.biometrics.AuthRippleController", lpparam.classLoader);
        if (rippleCtrlClass == null) return;

        try {
            Class<?> bioTypeClass = XposedHelpers.findClass(
                    "android.hardware.biometrics.BiometricSourceType", lpparam.classLoader);
            sFpSourceType = Enum.valueOf((Class<Enum>) bioTypeClass, "FINGERPRINT");
        } catch (Throwable ignored) {}

        for (Method m : rippleCtrlClass.getDeclaredMethods()) {
            if (m.getName().equals("access$showUnlockRippleInternal")) {
                m.setAccessible(true);
                sInternalMethod = m;
            } else if (m.getName().equals("showUnlockedRipple")) {
                m.setAccessible(true);
                sPublicMethod = m;
            }
        }

        XposedBridge.hookAllConstructors(rippleCtrlClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                sRippleController = param.thisObject;
            }
        });

        XposedBridge.hookAllMethods(rippleCtrlClass, "showUnlockedRipple", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object ctrl = param.thisObject;
                try {
                    XposedHelpers.setObjectField(ctrl, "fingerprintSensorLocation", SENSOR_POINT);
                } catch (Throwable ignored) {}
                for (Field f : ctrl.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(ctrl);
                    if (val instanceof View) prepareRippleView((View) val);
                }
                try {
                    Field mViewField = XposedHelpers.findFieldIfExists(ctrl.getClass().getSuperclass(), "mView");
                    if (mViewField != null) {
                        mViewField.setAccessible(true);
                        View v = (View) mViewField.get(ctrl);
                        if (v != null) prepareRippleView(v);
                    }
                } catch (Throwable ignored) {}
            }
        });

        Class<?> kumClass = XposedHelpers.findClassIfExists(
                "com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader);
        if (kumClass != null) {
            XposedBridge.hookAllMethods(kumClass, "onFingerprintAuthenticated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    long now = System.currentTimeMillis();
                    if (now - sLastRippleTime < 450 || sRippleController == null) return;
                    sLastRippleTime = now;

                    try {
                        if (sInternalMethod != null && sFpSourceType != null) {
                            if (sInternalMethod.getParameterCount() == 2) {
                                sInternalMethod.invoke(null, sRippleController, sFpSourceType);
                            } else {
                                sInternalMethod.invoke(sRippleController, sFpSourceType);
                            }
                        } else if (sPublicMethod != null) {
                            if (sPublicMethod.getParameterCount() == 0) {
                                sPublicMethod.invoke(sRippleController);
                            } else {
                                sPublicMethod.invoke(sRippleController, sFpSourceType);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void prepareRippleView(View v) {
        v.setVisibility(View.VISIBLE);
        if (v.getWidth() == 0 || v.getHeight() == 0) {
            DisplayMetrics dm = v.getResources().getDisplayMetrics();
            int w = dm.widthPixels > 0 ? dm.widthPixels : 1080;
            int h = dm.heightPixels > 0 ? dm.heightPixels : 2400;
            v.layout(0, 0, w, h);
        }
        try {
            XposedHelpers.callMethod(v, "setFingerprintSensorLocation", SENSOR_POINT, 0f);
        } catch (Throwable e1) {
            try {
                XposedHelpers.callMethod(v, "setFingerprintSensorLocation", SENSOR_POINT);
            } catch (Throwable ignored) {}
        }
    }
}
