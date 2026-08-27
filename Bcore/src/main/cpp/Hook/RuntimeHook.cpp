#include "RuntimeHook.h"
#import "JniHook/JniHook.h"
#include "BoxCore.h"
#include <dlfcn.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

// منع التحميل المزدوج
static bool secondLibLoaded = false;

static void tryLoadSecondLibrary(const char *firstLibPath) {
    if (secondLibLoaded) return;

    // نبني مسار libinject2.so من نفس مجلد libinject.so
    const char *pos = strstr(firstLibPath, "libinject.so");
    if (pos == nullptr) return;

    char secondPath[512] = {0};
    int prefixLen = pos - firstLibPath;
    strncpy(secondPath, firstLibPath, prefixLen);
    strcat(secondPath, "libinject2.so");

    // نتحقق أن الملف موجود أصلاً
    if (access(secondPath, F_OK) != 0) {
        ALOGD("No second library found at: %s", secondPath);
        return;
    }

    void *handle = dlopen(secondPath, RTLD_NOW | RTLD_GLOBAL);
    if (handle != nullptr) {
        ALOGD("Second library loaded successfully: %s", secondPath);
    } else {
        ALOGD("Failed to load second library: %s", dlerror());
    }

    secondLibLoaded = true;
}

HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, jstring name, jobject class_loader) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);

    jstring result = orig_nativeLoad(env, obj, name, class_loader);

    // بعد تحميل libinject.so نحاول تحميل libinject2.so
    if (strstr(nameC, "libinject.so")) {
        tryLoadSecondLibrary(nameC);
    }

    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

HOOK_JNI(jstring, nativeLoadNew, JNIEnv *env, jobject obj, jstring name, jobject class_loader,
         jobject caller) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);

    jstring result = orig_nativeLoadNew(env, obj, name, class_loader, caller);

    // نفس الشيء للإصدارات الجديدة من Android
    if (strstr(nameC, "libinject.so")) {
        tryLoadSecondLibrary(nameC);
    }

    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

void RuntimeHook::init(JNIEnv *env) {
    // نعيد تهيئة العلامة عند كل launch جديد
    secondLibLoaded = false;

    const char *className = "java/lang/Runtime";
    if (BoxCore::getApiLevel() >= __ANDROID_API_Q__) {
        JniHook::HookJniFun(env, className, "nativeLoad","(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
                            (void *) new_nativeLoadNew, (void **) (&orig_nativeLoadNew), true);
    } else {
        JniHook::HookJniFun(env, className, "nativeLoad","(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;",
                            (void *) new_nativeLoad, (void **) (&orig_nativeLoad), true);
    }
}
