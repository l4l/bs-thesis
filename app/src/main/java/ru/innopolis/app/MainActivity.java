package ru.innopolis.app;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final String CL_TAG = MainActivity.class.getName() + ":ClassLoader";
    private ArrayList<Class> loaded = new ArrayList<>();
    private Activity substituted;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        final TextView out = (TextView) findViewById(R.id.textView);
        final EditText in = (EditText) findViewById(R.id.editText);
        Button b = (Button)findViewById(R.id.button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String req = in.getText().toString();
                Log.d(TAG, req);
                out.setText(system(req));
            }
        });

        String app = "ru.innopolis.dummy";
        String path = getApkPath(app);
        if (path == null) {
            Log.d(TAG, "Not found path for " + app);
            return;
        } else {
            Log.d(TAG, "Found app path: " + path);
        }

        loadApk(path);
        loadMain();
        assert substituted != null;
        Log.d(CL_TAG, "loaded: " + substituted.getClass().getCanonicalName());
        invoke("f");
    }

    Object invoke(String name, Object... c) {
        if (substituted == null) return null;
        try {
            Method method = substituted.getClass().getMethod(name);
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            return method.invoke(substituted, c);
        } catch (Exception e) {
            Log.d(CL_TAG, "Can't execute " + name + " because of " + e.toString());
            return null;
        }
    }

    private String getApkPath(String name) {
        List<ApplicationInfo> apps = getPackageManager()
                .getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo pi : apps) {
            if (pi.packageName.equals(name)) return pi.sourceDir;
        }
        return null;
    }

    private void loadMain() {
        for (Class c: loaded) {
            if (Activity.class.isAssignableFrom(c)) {
                try {
                    substituted = (Activity) c.getConstructor().newInstance();
                } catch (Exception ignored) {}
            }
        }
    }

    private void loadApk(String path) {
        DexClassLoader loader = new DexClassLoader(path,
                getCacheDir().getAbsolutePath(), null, getClass().getClassLoader());
        for (String c: listClasses(path)) {
            try {
                loaded.add(loader.loadClass(c));
            } catch (ClassNotFoundException e) {
                Log.d(CL_TAG, "not found: " + c);
            }
        }
    }

    private ArrayList<String> listClasses(String apk) {
        ArrayList<String> classes = new ArrayList<>();
        try {
            DexFile file = new DexFile(apk);
            Enumeration<String> entries = file.entries();
            while (entries.hasMoreElements()) {
                String c = entries.nextElement();
                if (c.contains("android")) continue;
                if (c.endsWith(".R")) continue;
                classes.add(c);
                Log.d(CL_TAG, c);
            }
        } catch (IOException ignored) {}
        return classes;
    }

    public native String system(String s);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
