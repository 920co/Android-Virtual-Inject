package com.vcore.core;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;

import com.vcore.BlackBoxCore;
import com.vcore.app.BActivityThread;

@SuppressLint({"UnsafeDynamicallyLoadedCode", "SdCardPath"})
public class NativeCore {
    public static final String TAG = "NativeCore";
    private static boolean isInjected = false;

    static {
        System.loadLibrary("vcore");

        if (!isInjected) {
            Log.i(TAG, "Loading libinject.so from cache directory");
            if (new File("/data/data/com.reveny.virtualinject/cache/libinject.so").exists()) {
                Log.i(TAG, "Loading libinject.so from cache directory");
                System.load("/data/data/com.reveny.virtualinject/cache/libinject.so");
            } else {
                Log.e(TAG, "libinject.so not found in cache directory");
            }

            // [إضافة] تحميل مكتبة ثانية اختيارية إن وُجدت
            File secondLib = new File("/data/data/com.reveny.virtualinject/cache/libinject2.so");
            if (secondLib.exists()) {
                Log.i(TAG, "Loading libinject2.so from cache directory");
                try {
                    System.load(secondLib.getAbsolutePath());
                    Log.i(TAG, "libinject2.so loaded successfully");
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to load libinject2.so: " + t.getMessage());
                }
            } else {
                Log.i(TAG, "No libinject2.so found, skipping");
            }

        } else {
            Log.i(TAG, "libinject.so already loaded");
        }
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
