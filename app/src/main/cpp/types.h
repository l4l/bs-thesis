#pragma once

#define log(f, ...) __android_log_print(ANDROID_LOG_VERBOSE, "NativeLib", f, ##__VA_ARGS__);

typedef uint8_t             u1;
typedef uint16_t            u2;
typedef uint32_t            u4;
typedef uint64_t            u8;
typedef int8_t              s1;
typedef int16_t             s2;
typedef int32_t             s4;
typedef int64_t             s8;
typedef union JValue {
    u1      z;
    s1      b;
    u2      c;
    s2      s;
    s4      i;
    s8      j;
    float   f;
    double  d;
    void*   l;
} JValue;

typedef int (*fn)(void*, JValue*);


struct Object {
    /* ptr to class object */
    void*    clazz;
    /*
     * A word containing either a "thin" lock or a "fat" monitor.  See
     * the comments in Sync.c for a description of its layout.
     */
    u4              lock;
};

struct ArrayObject {
    /* number of elements; immutable after init */
    u4              length;
    /*
     * Array contents; actual size is (length * sizeof(type)).  This is
     * declared as u8 so that the compiler inserts any necessary padding
     * (e.g. for EABI); the actual allocation may be smaller than 8 bytes.
     */
    u8              contents[1];
};

struct DexOrJar {
    char*       fileName;
    bool        isDex;
    bool        okayToFree;
    void*       pRawDexFile;
    void*       pJarFile;
    u1*         pDexMemory; // malloc()ed memory, if any
};