package ru.innopolis.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private ArrayList<Class> loaded = new ArrayList<>();
    private Activity substituted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        String s = "/data/app/ru.innopolis.dummy.apk";

        loadApk(s);
        for (Class c: loaded) {
            Log.d(TAG + ":ClassLoading", "loaded: " + c.getCanonicalName());
        }

        for (Class c: loaded) {
            if (c.getSuperclass().equals(Activity.class)) {
                try {
                    substituted = (Activity) c.getConstructor().newInstance();
                    c.getDeclaredMethod("f").invoke(substituted);
                } catch (Exception e) {
                    Log.d(TAG + ":ClassLoading", "method not resolved: " + c.getCanonicalName());
                }
            }
        }
    }

    private void loadApk(String apk) {
        DexClassLoader loader = new DexClassLoader(apk, getCacheDir().getAbsolutePath(), null, getClass().getClassLoader());
        for (String c: listClasses(apk)) {
            try {
                loaded.add(loader.loadClass(c));
            } catch (ClassNotFoundException e) {
                Log.d(TAG + ":ClassLoading", "not found: " + c);
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
                Log.d(TAG + ":ClassLoading", c);
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