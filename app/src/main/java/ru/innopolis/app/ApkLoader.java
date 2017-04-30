package ru.innopolis.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import com.android.dx.gen.Type;
import com.android.dx.io.ClassData;
import com.android.dx.io.ClassDef;
import com.android.dx.io.DexBuffer;
import com.android.dx.io.MethodId;
import com.android.dx.io.ProtoId;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import kellinwood.security.zipsigner.ZipSigner;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class ApkLoader {
    private static final String TAG = MainActivity.class.getName();
    private final Context ctx;
    private final String merged;
    private final String path;
    private final String pkg;
    private DexBuffer buffer;

    private static final String CLASS_FILENAME = "classes.dex";
    private static final String PKG_SUFFIX = "-fixed";

    public ApkLoader(Context c, String pkg) throws IOException {
        ctx = c;
        this.pkg = pkg;
        path = getApkPath(ctx, pkg);
        merged = ctx.getFilesDir().getPath() + "/" + CLASS_FILENAME;
        buffer = getDex();
    }

    /**
     * Perform patching dangerous methods
     * Patch TelephonyManager.getSystemService so far
     * @throws IOException
     */
    public void patch() throws IOException {
        addStub();

        ClassDef stub = findClass("Lru/innopolis/Stub;");
        ClassDef forPatch = findClass("Lru/innopolis/dummy/MainActivity;");

        for (ClassData.Method m : findAllMethods(forPatch)) {
            int method = findMethod(stub, "Ljava/lang/String;");
            if (method == 0) continue;

            Log.d(TAG, "Traversing: " + getMethodName(m));

            MethodBuffer buf = new MethodBuffer(buffer.getBytes(), m.getCodeOffset());
            HashMap<Short, Short> map = new HashMap<>();
            map.put((short)getMethodIdx("getDeviceId"), (short)method);
            buf.traverse(map);
        }
        updateChecksum(buffer.getBytes());
        buffer.writeTo(new File(merged));
    }

    /**
     * Recreate the same apk but with new classes.dex
     * @throws IOException
     */
    public void repack() throws IOException {
        ZipFile orig = new ZipFile(path);
        String apk = ctx.getCacheDir() + "/" + pkg + PKG_SUFFIX + ".apk";
        ZipOutputStream repacked = new ZipOutputStream(
                new FileOutputStream(apk));
        Enumeration e = orig.entries();
        byte buf[] = new byte[512];
        do {
            ZipEntry entry = (ZipEntry) e.nextElement();
            InputStream stream;
            // This will be generated at sign()
            if (entry.getName().startsWith("META-INF")) continue;
            // Load patched classfile
            if (entry.getName().equals(CLASS_FILENAME)) {
                repacked.putNextEntry(new ZipEntry(CLASS_FILENAME));
                stream = new FileInputStream(merged);
            } else {
                repacked.putNextEntry(entry);
                stream = orig.getInputStream(entry);
            }

            while (0 < stream.available()) {
                int len = stream.read(buf);
                repacked.write(buf, 0, len);
            }
            stream.close();
            repacked.closeEntry();
        } while (e.hasMoreElements());
        orig.close();
        repacked.close();
    }

    private static void updateChecksum(byte buffer[]) {
        Adler32 a = new Adler32();
        final int magicSize = 8;
        final int checsumSize = 4;
        final int offset = magicSize + checsumSize;
        a.update(buffer, offset, buffer.length - offset);
        long checksum = a.getValue();
        // Write in little-endian format
        buffer[magicSize] = (byte) checksum;
        checksum >>= 8;
        buffer[magicSize + 1] = (byte) checksum;
        checksum >>= 8;
        buffer[magicSize + 2] = (byte) checksum;
        checksum >>= 8;
        buffer[magicSize + 3] = (byte) checksum;
    }

    /**
     * Perform apk signing
     * @return path of the apk
     */
    public String sign() {
        String signed = null;
        try {
            ZipSigner signer = new ZipSigner();
            signer.setKeymode("testkey");
            signed = getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                            "/" + pkg + PKG_SUFFIX + "-sig.apk";
            signer.signZip(ctx.getCacheDir() + "/" + pkg + PKG_SUFFIX + ".apk",
                    signed);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return signed;
    }

    /**
     * Add stub class with static method for further patching
     * @throws IOException
     */
    private void addStub() throws IOException {
        String fname = ctx.getCacheDir() + File.separator + StubGenerator.CLASS_NAME + ".dex";
        StubGenerator gen = new StubGenerator(fname);
        gen.generateMethod("test", Type.get("Ljava/lang/String;"));
        DexBuffer stubBuffer = gen.save();

        DexMerger merger = new DexMerger(buffer, stubBuffer, CollisionPolicy.KEEP_FIRST);
        // merge() will optimize out generated stub, so need to call private method
        buffer = (DexBuffer) invoke(merger, "mergeDexBuffers");
        buffer.writeTo(new File(fname));
    }

    private String getMethodName(MethodId id) {
        return buffer.strings().get(id.getNameIndex());
    }

    private String getMethodName(ClassData.Method m) {
        int i = m.getMethodIndex();
        MethodId id = buffer.methodIds().get(i);
        return getMethodName(id);
    }

    /**
     * Get index of the method
     * @param name of the method
     * @return found index or -1 otherwise
     */
    private int getMethodIdx(String name) {
        int nameIdx = buffer.strings().indexOf(name);
        List<MethodId> ids = buffer.methodIds();
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).getNameIndex() == nameIdx) return i;
        }
        return -1;
    }

    /**
     * Search for the class in the buffer
     * @param className name to find
     * @return found class definition or null
     */
    private ClassDef findClass(String className) {
        for (ClassDef classDef : buffer.classDefs()) {
            String name = classDef.toString();
            if (name.startsWith(className)) return classDef;
        }
        return null;
    }

    /**
     * Find method in class with exact signature as provided method
     * @param classDef class where to search
     * @param m method against to compare the signature
     * @return found method index or 0
     */
    private int findMethod(ClassDef classDef, ClassData.Method m) {
        List<String> tnames = buffer.typeNames();
        MethodId id = buffer.methodIds().get(m.getMethodIndex());
        ProtoId proto = buffer.protoIds().get(id.getProtoIndex());

        short[] types = buffer.readTypeList(proto.getParametersOffset()).getTypes();
        String params[] = new String[types.length];
        for (int i = 0; i < types.length; i++) params[i] = tnames.get(types[i]);

        return findMethod(classDef, tnames.get(proto.getReturnTypeIndex()), params);
    }

    /**
     * Find method in class with following return and parameters types
     * @param classDef class where to search
     * @param ret string that represent return type in dex-format form
     * @param params array of method parameter types in dex-format form
     * @return found method index or 0
     */
    private int findMethod(ClassDef classDef, String ret, String... params) {
        List<String> tnames = buffer.typeNames();
        List<MethodId> ids = buffer.methodIds();
        List<ProtoId> protos = buffer.protoIds();

    outer:
        for (ClassData.Method m : findAllMethods(classDef)) {
            MethodId id = ids.get(m.getMethodIndex());
            ProtoId proto = protos.get(id.getProtoIndex());

            if (!tnames.get(proto.getReturnTypeIndex()).equals(ret)) continue;

            short[] types = buffer.readTypeList(proto.getParametersOffset()).getTypes();
            if (params.length != types.length) continue;
            for (int i = 0; i < params.length; i++) {
                if (!tnames.get(types[i]).equals(params[i])) continue outer;
            }
            return m.getMethodIndex();
        }
        return 0;
    }

    /**
     * Search for direct and virtual methods for further patching
     * @param classDef class where to search
     * @return found arrays of methods
     */
    private ClassData.Method[] findAllMethods(ClassDef classDef) {
        ClassData data = buffer.readClassData(classDef);
        ClassData.Method[] virtual = data.getVirtualMethods();
        ClassData.Method[] direct = data.getDirectMethods();
        ClassData.Method[] m = new ClassData.Method[virtual.length + direct.length];
        System.arraycopy(virtual, 0, m, 0, virtual.length);
        System.arraycopy(direct, 0, m, virtual.length, direct.length);
        return m;
    }

    /**
     * Converts internal type representation form to human-readable format
     * @param tnames dex-file typenames section
     * @param types array of indexes in that section
     * @return string in human-readable format
     */
    private String humanify(List<String> tnames, short[] types) {
        StringBuilder buf = new StringBuilder();
        for (short t : types) {
            buf.append(humanify(tnames.get(t)));
            buf.append(", ");
        }
        return buf.toString();
    }

    /**
     * Converts internal type representation form to human-readable format
     * @param type string to convert
     * @return string in human-readable format
     */
    private String humanify(String type) {
        if (type.startsWith("[")) return humanify(type.substring(1) + "[]");

        if (type.startsWith("L")) return type.substring(1, type.length() - 1).replace('/', '.');

        switch (type) {
            case "B": return "byte";
            case "C": return "char";
            case "D": return "double";
            case "F": return "float";
            case "I": return "int";
            case "J": return "long";
            case "S": return "short";
            case "V": return "void";
            case "Z": return "boolean";
        }
        return null;
    }

    private DexBuffer getDex() throws IOException {
        ZipFile file = new ZipFile(path);
        ZipEntry e = file.getEntry(CLASS_FILENAME);
        return new DexBuffer(file.getInputStream(e));
    }

    public static String getApkPath(Context ctx, String name) {
        List<ApplicationInfo> apps = ctx.getPackageManager()
                .getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo pi : apps) {
            if (pi.packageName.equals(name)) return pi.sourceDir;
        }
        return null;
    }

    /**
     * Finds and invokes method without parameters
     * @param o method's object
     * @param name of the method
     * @return value of method call
     */
    public static Object invoke(Object o, String name) {
        try {
            Method m = findDeclaredMethod(o, name);
            return m.invoke(o);
        } catch (Exception e) {
            Log.d(TAG, "Can't execute " + name + " because of " + e.toString());
            return null;
        }
    }

    /**
     * Wrapper over Class.getDeclaredMethod
     * @param o method's object
     * @param name of the method
     * @param clazz param types
     * @return value of method call
     */
    public static Method findDeclaredMethod(Object o, String name, Class... clazz) {
        if (o == null) return null;
        try {
            Method method = o.getClass().getDeclaredMethod(name, clazz);
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method;
        } catch (Exception e) {
            Log.d(TAG, "Can't execute " + name + " because of " + e.toString());
            return null;
        }
    }

//    public <T> T createClass(String n) throws Exception {
//        DexClassLoader cl = new DexClassLoader(ctx.getCacheDir() + "/" + pkg + PKG_SUFFIX + ".apk",
//                ctx.getCacheDir().getAbsolutePath(), null, ctx.getClassLoader());
//        return (T) cl.loadClass(n).getConstructor().newInstance();
//    }
//
//        for (ClassDef classDef : buffer.classDefs()) {
//            String className;
//            try {
//                className = classDef.toString();
//            } catch (IllegalArgumentException e) {continue;}
//            if (!className.contains("innopolis")) continue;
//            Log.d(TAG, className);
//            ClassData data = buffer.readClassData(classDef);
//
//            List<String> tnames = buffer.typeNames();
//            List<Integer> types = buffer.typeIds();
//            List<String> strings = buffer.strings();
//            List<FieldId> fields = buffer.fieldIds();
//            List<ProtoId> protos = buffer.protoIds();
//            List<MethodId> methods = buffer.methodIds();
//
//            Log.d(TAG, className + " method offsets:");
//            for (ClassData.Method m : data.getDirectMethods()) {
//                Code code = buffer.readCode(m);
//
//                int i = m.getMethodIndex();
//                MethodId id = methods.get(i);
//                String n = strings.get(id.getNameIndex());
//                if (n.equals("<init>")) continue;
//                ProtoId proto = protos.get(id.getProtoIndex());
//                String ret = humanify(tnames.get(proto.getReturnTypeIndex()));
//                String params = humanify(tnames, buffer.readTypeList(proto.getParametersOffset()).getTypes());
//                Log.d(TAG, "\t" + m.getCodeOffset() + "\n" +
//                        ret + " " + n + " (" + params + ") " +
//                        proto.toString());
//            }
//        }
}
