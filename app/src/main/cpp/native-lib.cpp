#include <android/log.h>
#include <jni.h>
#include <cerrno>
#include <string>
#include <iostream>
#include <array>
#include <unistd.h>

#define APPNAME "App"
#define log(f, ...) __android_log_print(ANDROID_LOG_VERBOSE, APPNAME, f, ##__VA_ARGS__);

extern "C"
JNIEXPORT jstring JNICALL
Java_ru_innopolis_app_MainActivity_system(JNIEnv *env, jobject, jstring s_) {
    const char *s = env->GetStringUTFChars(s_, 0);

    FILE *f = popen(s, "r");

    if (!f) {
        log("Error: %s", strerror(errno));
        return nullptr;
    }

    std::string ret;
    std::array<char, 128> buffer;
    while (!feof(f)) {
        if (fgets(buffer.data(), 128, f) != NULL)
            ret += buffer.data();
    }
    pclose(f);

    env->ReleaseStringUTFChars(s_, s);

    return env->NewStringUTF(ret.c_str());
}
