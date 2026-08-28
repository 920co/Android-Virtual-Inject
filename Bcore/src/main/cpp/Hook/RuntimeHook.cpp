#include "RuntimeHook.h"
#import "JniHook/JniHook.h"
#include "BoxCore.h"
#include <dlfcn.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

static bool secondLibLoaded = false;

static void tryLoadSecondLibrary() {
    if (secondLibLoaded) return;
    secondLibLoaded = true;

    // نجرب المسارين الممكنين لـ cache الـ host app
    const char* candidates[] = {
        "/data/data/com.reveny.virtualinject/cache/libinject2.so",
        "/data/user/0/com.reveny.virtualinject/cache/libinject2.so",
        nullptr
    };

    for (int i = 0; candidates[i] != nullptr; i++) {
        if (access(candidates[i], F_OK) != 0) {
            ALOGD("libinject2.so not found at: %s", candidates[i]);
            continue;
        }

        void *handle = dlopen(candidates[i], RTLD_NOW | RTLD_GLOBAL);
        if (handle != nullptr) {
            ALOGD("Second library loaded: %s", candidates[i]);
            return;
        } else {
            ALOGD("dlopen failed for %s: %s", candidates[i], dlerror());
        }
    }

    ALOGD("Could not load libinject2.so from any known path");
}

HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, jstring name, jobject class_loader) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);

    jstring result = orig_nativeLoad(env, obj, name, class_loader);

    if (strstr(nameC, "libinject.so")) {
        tryLoadSecondLibrary();
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
        tryLoadSecondLibrary();
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
