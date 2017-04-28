package ru.innopolis.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;

import com.android.dx.io.ClassDef;
import com.android.dx.io.DexBuffer;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;

public class ApkLoader {
    private static final String TAG = MainActivity.class.getName();
    private final ArrayList<Class> loaded;
    private final Context ctx;
    private final String merged;
    private final String path;
    private final String pkg;
    private DexBuffer buffer;

    private static final String CLASS_FILENAME = "classes.dex";
    private static final String PKG_SUFFIX = "-fixed";

    public ApkLoader(Context c, String pkg) throws IOException {
        loaded = new ArrayList<>();
        ctx = c;
        this.pkg = pkg;
        path = getApkPath(ctx, pkg);
        merged = ctx.getFilesDir().getPath() + "/" + CLASS_FILENAME;

        buffer = getDex();
        for (ClassDef classDef : buffer.classDefs()) {
//            ClassData data = buffer.readClassData(classDef);
            String msg;
            try {
                msg = classDef.toString();
            } catch (IllegalArgumentException e) {continue;}
            if (!msg.contains("innopolis")) continue;
            Log.d(TAG, msg);
        }

        if (!isWithStub()) addStub();
    }

    private boolean isWithStub() {
        File f = new File(merged);

        return f.exists() && f.getTotalSpace() > 0x70; // magic means header size, simple heuristic
    }

    private void addStub() throws IOException {
        String fname = StubGenerator.CLASS_NAME + ".dex";
        StubGenerator gen = new StubGenerator(fname);
        gen.generateMethod("test");
        DexBuffer stubBuffer = gen.save();

        buffer = new DexMerger(buffer, stubBuffer, CollisionPolicy.KEEP_FIRST).merge();
        buffer.writeTo(new File(merged));
    }

    public void repack() throws IOException {
        ZipFile orig = new ZipFile(path);
        ZipOutputStream repacked = new ZipOutputStream(
                new FileOutputStream(ctx.getCacheDir() + "/" + pkg + PKG_SUFFIX + ".apk"));
        Enumeration e = orig.entries();
        byte buf[] = new byte[512];
        do {
            ZipEntry entry = (ZipEntry) e.nextElement();
            InputStream stream;
            if (entry.getName().equals(CLASS_FILENAME)) {
                repacked.putNextEntry(new ZipEntry(CLASS_FILENAME));
                stream = new FileInputStream(merged);
            } else {
                try {
                    repacked.putNextEntry(entry);
                } catch (ZipException ex) {
                    Log.d(TAG, ex.getMessage());
                    continue;
                }
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

    private DexBuffer getDex() throws IOException {
        ZipFile file = new ZipFile(path);
        ZipEntry e = file.getEntry(CLASS_FILENAME);
        return new DexBuffer(file.getInputStream(e));
    }

    public <T> T createClass(String n) throws Exception {
        DexClassLoader cl = new DexClassLoader(ctx.getCacheDir() + "/" + pkg + PKG_SUFFIX + ".apk",
                ctx.getCacheDir().getAbsolutePath(), null, ctx.getClassLoader());
        return (T) cl.loadClass(n).getConstructor().newInstance();
    }

    public static String getApkPath(Context ctx, String name) {
        List<ApplicationInfo> apps = ctx.getPackageManager()
                .getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo pi : apps) {
            if (pi.packageName.equals(name)) return pi.sourceDir;
        }
        return null;
    }

    public static Object invoke(Object o, String name, Object... c) {
        if (o == null) return null;
        try {
            Method method = o.getClass().getMethod(name);
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method.invoke(o, c);
        } catch (Exception e) {
            Log.d(TAG, "Can't execute " + name + " because of " + e.toString());
            return null;
        }
    }
}
