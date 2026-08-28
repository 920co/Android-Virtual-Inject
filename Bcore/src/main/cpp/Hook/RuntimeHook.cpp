#include "RuntimeHook.h"
#import "JniHook/JniHook.h"
#include "BoxCore.h"
#include <dlfcn.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <string>

static bool secondLibLoaded = false;

// نحصل على cache dir الحقيقي للعملية الحالية عبر استدعاء
// ActivityThread.currentActivityThread().getApplication().getCacheDir()
// هذا يعمل بغض النظر عن اسم الحزمة أو أي لاحقة (.debug مثلاً)
static std::string getRealCacheDir(JNIEnv *env) {
    std::string result;

    jclass activityThreadClass = env->FindClass("android/app/ActivityThread");
    if (activityThreadClass == nullptr) { env->ExceptionClear(); return result; }

    jmethodID currentActivityThreadMethod = env->GetStaticMethodID(
        activityThreadClass, "currentActivityThread", "()Landroid/app/ActivityThread;");
    if (currentActivityThreadMethod == nullptr) { env->ExceptionClear(); return result; }

    jobject activityThread = env->CallStaticObjectMethod(activityThreadClass, currentActivityThreadMethod);
    if (activityThread == nullptr) { env->ExceptionClear(); return result; }

    jmethodID getApplicationMethod = env->GetMethodID(
        activityThreadClass, "getApplication", "()Landroid/app/Application;");
    if (getApplicationMethod == nullptr) { env->ExceptionClear(); return result; }

    jobject application = env->CallObjectMethod(activityThread, getApplicationMethod);
    if (application == nullptr) { env->ExceptionClear(); return result; }

    jclass contextClass = env->FindClass("android/content/Context");
    if (contextClass == nullptr) { env->ExceptionClear(); return result; }

    jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
    if (getCacheDirMethod == nullptr) { env->ExceptionClear(); return result; }

    jobject cacheDirFile = env->CallObjectMethod(application, getCacheDirMethod);
    if (cacheDirFile == nullptr) { env->ExceptionClear(); return result; }

    jclass fileClass = env->FindClass("java/io/File");
    if (fileClass == nullptr) { env->ExceptionClear(); return result; }

    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    if (getAbsolutePathMethod == nullptr) { env->ExceptionClear(); return result; }

    jstring pathString = (jstring) env->CallObjectMethod(cacheDirFile, getAbsolutePathMethod);
    if (pathString == nullptr) { env->ExceptionClear(); return result; }

    const char *pathChars = env->GetStringUTFChars(pathString, nullptr);
    if (pathChars != nullptr) {
        result = pathChars;
        env->ReleaseStringUTFChars(pathString, pathChars);
    }

    return result;
}

static void tryLoadSecondLibrary(JNIEnv *env) {
    if (secondLibLoaded) return;
    secondLibLoaded = true;

    std::string cacheDir = getRealCacheDir(env);

    if (cacheDir.empty()) {
        ALOGD("Could not resolve real cache dir, falling back to hardcoded paths");
        const char* fallbacks[] = {
            "/data/data/com.reveny.virtualinject/cache/libinject2.so",
            "/data/user/0/com.reveny.virtualinject/cache/libinject2.so",
            nullptr
        };
        for (int i = 0; fallbacks[i] != nullptr; i++) {
            if (access(fallbacks[i], F_OK) == 0) {
                void *h = dlopen(fallbacks[i], RTLD_NOW | RTLD_GLOBAL);
                ALOGD("Fallback dlopen %s -> %s", fallbacks[i], h ? "OK" : dlerror());
                if (h) return;
            }
        }
        return;
    }

    std::string fullPath = cacheDir + "/libinject2.so";
    ALOGD("Resolved cache dir: %s", cacheDir.c_str());

    if (access(fullPath.c_str(), F_OK) != 0) {
        ALOGD("libinject2.so not found at: %s", fullPath.c_str());
        return;
    }

    void *handle = dlopen(fullPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle != nullptr) {
        ALOGD("Second library loaded successfully: %s", fullPath.c_str());
    } else {
        ALOGD("dlopen failed for %s: %s", fullPath.c_str(), dlerror());
    }
}

HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, jstring name, jobject class_loader) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);

    jstring result = orig_nativeLoad(env, obj, name, class_loader);

    if (strstr(nameC, "libinject.so")) {
        tryLoadSecondLibrary(env);
    }

    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

HOOK_JNI(jstring, nativeLoadNew, JNIEnv *env, jobject obj, jstring name, jobject class_loader,
         jobject caller) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);

    jstring result = orig_nativeLoadNew(env, obj, name, class_loader, caller);

    if (strstr(nameC, "libinject.so")) {
        tryLoadSecondLibrary(env);
    }

    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

void RuntimeHook::init(JNIEnv *env) {
    secondLibLoaded = false;

    const char *className = "java/lang/Runtime";
    if (BoxCore::getApiLevel() >= __ANDROID_API_Q__) {
        JniHook::HookJniFun(env, className, "nativeLoad",
            "(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
            (void *) new_nativeLoadNew, (void **) (&orig_nativeLoadNew), true);
    } else {
        JniHook::HookJniFun(env, className, "nativeLoad",
            "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;",
            (void *) new_nativeLoad, (void **) (&orig_nativeLoad), true);
    }
}
