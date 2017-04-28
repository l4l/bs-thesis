#include <android/log.h>
#include <jni.h>
#include <cerrno>
#include <string>
#include <iostream>
#include <array>
#include <dlfcn.h>
#include <cstring>
#include <map>
#include "types.h"

const std::pair<const char*, const char*> DF_TABLE[] = {
        {"openDexFile",             "(Ljava/lang/String;Ljava/lang/String;I)I"},
        {"openDexFile",             "([B)I"}, //openDexFile_bytearray
        {"closeDexFile",            "(I)V"},
        {"defineClass",             "(Ljava/lang/String;Ljava/lang/ClassLoader;I)Ljava/lang/Class;"},
        {"getClassNameList",        "(I)[Ljava/lang/String;"},
        {"isDexOptNeeded",          "(Ljava/lang/String;)Z"},
};

void* method_lookup(JNINativeMethod *table, const char *name, const char *sig) {
    for (int i = 0; table[i].name; ++i) {
        if (!strcmp(table[i].name, name) &&
            !strcmp(table[i].signature, sig)) {
            return table[i].fnPtr;
        }
    }
    return nullptr;
}

void *libdvm = dlopen("libdvm.so", RTLD_LAZY);

static int hashcmpDexOrJar(const void* tableVal, const void* newVal) {
    return *(int*) &newVal - *(int*) &tableVal;
}

static void* openDexFile_bytearray(ArrayObject* ao) {
    u4 length;
    u1* pBytes;
    void* pRawDexFile;
    DexOrJar* pDexOrJar = NULL;
    if (ao == NULL) {
        return 0;
    }
    /* TODO: Avoid making a copy of the array. (note array *is* modified) */
    length = ao->length;
    pBytes = (u1*) malloc(length);
    if (pBytes == NULL) {
        return 0;
    }
    memcpy(pBytes, ao->contents, length);
    auto dvmRawDexFileOpenArray = (int(*)(u1*, u4, void*))dlsym(libdvm, "_Z22dvmRawDexFileOpenArrayPhjPP10RawDexFile");
    if (dvmRawDexFileOpenArray(pBytes, length, &pRawDexFile) != 0) {
        log("Unable to open in-memory DEX file");
        free(pBytes);
        return 0;
    }
    log("Opening in-memory DEX");
    pDexOrJar = (DexOrJar*) malloc(sizeof(DexOrJar));
    pDexOrJar->isDex = true;
    pDexOrJar->pRawDexFile = pRawDexFile;
    pDexOrJar->pDexMemory = pBytes;
    pDexOrJar->fileName = strdup("<memory>"); // Needs to be free()able.
    auto addToDexFileTable = (void(*)(void*))dlsym(libdvm, "addToDexFileTable");

    //addToDexFileTable
    u4 hash = *(u4*) &pDexOrJar;
    void* result;

    auto dvmHashTableLock = (void(*)(void*))dlsym(libdvm, "_Z16dvmHashTableLockP9HashTable");
    auto gDvm = (char*)dlsym(libdvm, "gDvm");
    void *userDexFiles = gDvm + 0x328;
    dvmHashTableLock(userDexFiles);
    typedef void*(*hashtablelookup_t)(void*, u4, void*, int(*)(const void*, const void*), bool);
    auto dvmHashTableLookup = (hashtablelookup_t)dlsym(libdvm, "_Z18dvmHashTableLookupP9HashTablejPvPFiPKvS3_Eb");
    result = dvmHashTableLookup(userDexFiles, hash, pDexOrJar, hashcmpDexOrJar, true);
    auto dvmHashTableUnlock = (void(*)(void*))dlsym(libdvm, "_Z18dvmHashTableUnlockP9HashTable");
    dvmHashTableUnlock(userDexFiles);

    if (result != pDexOrJar) {
        log("Pointer has already been added?");
        ((void(*)(void))dlsym(libdvm, "dvmAbort"))();
    }

    pDexOrJar->okayToFree = true;
    return pDexOrJar;
}

extern "C"
JNIEXPORT jint JNICALL
Java_ru_innopolis_app_ApkLoader_load_1dex(JNIEnv *env, jobject instance, jbyteArray a) {
    if (libdvm == nullptr) log("%s", dlerror());
//    auto sym = dlsym(libdvm, "_Z12dexFileParsePKhji");
//    jbyte *data = env->GetByteArrayElements(a, 0);
//    DexFile *f = ((dexFileParse)sym)((const u1 *) data, env->GetArrayLength(a), 0);
//    return 0;
    auto table = (JNINativeMethod *) dlsym(libdvm, "dvm_dalvik_system_DexFile");
    if (table == nullptr) {
        log("%s", dlerror());
        return 0;
    }
    auto t = DF_TABLE[1];
    fn f = (fn) method_lookup(table, t.first, t.second);

//    void *ao = a;
    auto len = env->GetArrayLength(a);
    auto ao = (ArrayObject *) malloc(sizeof(u4) + len);
    ao->length = len;
    env->GetByteArrayRegion(a, 0, len, (jbyte*)&ao->contents[0]);
    JValue v;
    u8 i = (u8)openDexFile_bytearray(ao);
    return i;
//    f(&ao, &v);
//    return v.i;
}

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
