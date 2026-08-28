package com.vcore.core;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.vcore.BlackBoxCore;
import com.vcore.app.BActivityThread;

@SuppressLint({"UnsafeDynamicallyLoadedCode", "SdCardPath"})
public class NativeCore {
    public static final String TAG = "NativeCore";
    private static boolean isInjected = false;

    // الحد الأقصى للانتظار قبل تحميل مكتبة (بالمللي ثانية).
    // لو اللعبة تستخدم IL2CPP (Unity)، سننتظر ظهور libil2cpp.so في الذاكرة
    // قبل تحميل مكتبتنا، لتفادي تعطل ناتج عن الوصول لدوال المحرك قبل تحميله.
    // لو لم تظهر خلال هذه المهلة (تطبيق عادي بدون IL2CPP)، نُحمّل المكتبة كالمعتاد.
    private static final long IL2CPP_WAIT_TIMEOUT_MS = 8000;
    private static final long IL2CPP_POLL_INTERVAL_MS = 50;

    static {
        System.loadLibrary("vcore");

        if (!isInjected) {
            loadIfExists("/data/data/com.reveny.virtualinject/cache/libinject.so", "libinject.so");
            loadIfExists("/data/data/com.reveny.virtualinject/cache/libinject2.so", "libinject2.so");
        } else {
            Log.i(TAG, "libinject.so already loaded");
        }
    }

    // نتحقق من وجود il2cpp في خريطة ذاكرة العملية الحالية
    private static boolean isIl2CppLoaded() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("libil2cpp.so")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // نتجاهل ونعتبرها غير محملة بعد
        }
        return false;
    }

    private static void loadIfExists(String path, String label) {
        File libFile = new File(path);
        if (!libFile.exists()) {
            Log.i(TAG, label + " not found, skipping");
            return;
        }

        // ننتظر في خيط منفصل حتى يظهر il2cpp أو تنتهي المهلة، ثم نُحمّل المكتبة.
        // هذا لا يؤخر تطبيقات لا تستخدم IL2CPP لأكثر من المهلة القصوى فقط،
        // ويحل مشكلة الحقن قبل جاهزية محرك الألعاب.
        new Thread(() -> {
            long waited = 0;
            while (!isIl2CppLoaded() && waited < IL2CPP_WAIT_TIMEOUT_MS) {
                try {
                    Thread.sleep(IL2CPP_POLL_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
                waited += IL2CPP_POLL_INTERVAL_MS;
            }

            try {
                Log.i(TAG, "Loading " + label + " (waited " + waited + "ms)");
                System.load(path);
                Log.i(TAG, label + " loaded successfully");
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load " + label + ": " + t.getMessage());
            }
        }, "InjectLoader-" + label).start();
    }

    public static native void init(int apiLevel);

    public static native void enableIO();

    public static native void addWhiteList(String path);

    public static native void addIORule(String targetPath, String relocatePath);

    private static native void nativeIORedirect(String origPath, String newPath);

    public static native void hideXposed();

    @Keep
    public static int getCallingUid(int origCallingUid) {
        if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID) {
            return origCallingUid;
        }
        if (origCallingUid > Process.LAST_APPLICATION_UID) {
            return origCallingUid;
        }

        if (origCallingUid == BlackBoxCore.getHostUid()) {
            int callingPid = Binder.getCallingPid();
            int bUid = BlackBoxCore.getBPackageManager().getUidByPid(callingPid);
            if (bUid != -1) {
                return bUid;
            }
            return BActivityThread.getCallingBUid();
        }
        return origCallingUid;
    }

    @Keep
    public static String redirectPath(String path) {
        return IOCore.get().redirectPath(path);
    }

    @Keep
    public static File redirectPath(File path) {
        return IOCore.get().redirectPath(path);
    }
}
