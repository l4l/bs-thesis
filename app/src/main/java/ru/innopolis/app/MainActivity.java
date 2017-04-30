package ru.innopolis.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final int UNINSTALL_CODE = 123;
    private String signed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        final TextView out = (TextView) findViewById(R.id.textView);
        final EditText in = (EditText) findViewById(R.id.editText);
        Button b = (Button)findViewById(R.id.button);
        b.setOnClickListener(view -> {
            String req = in.getText().toString();
            Log.d(TAG, req);
            out.setText(system(req));
        });

        final String app = "ru.innopolis.dummy";

        try {
            ApkLoader loader = new ApkLoader(this, app);
            loader.patch();
            loader.repack();
            signed = loader.sign();
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return;
        }

        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app));
        startActivityForResult(uninstallIntent, UNINSTALL_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UNINSTALL_CODE) {
            Intent promptInstall = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.fromFile(new File(signed)),
                            "application/vnd.android.package-archive")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(promptInstall);
        }
    }

//    /**
//     * Dynamic Activity loading and substitution of current Activity
//     * doesn't work because it's hard to create context without intensive
//     * intervention to the system
//     */
//    private void load() {
//        try {
//            substituted = loader.createClass(app + ".MainActivity");//todo: grab from manifest
//        } catch (Exception e) {
//            Log.d(TAG, e.getMessage());
//            System.exit(-1);
//        }
//        assert substituted != null;
//
//        try {
//            copy();
//            ApkLoader.findDeclaredMethod(substituted, "onCreate", Bundle.class).invoke(substituted);
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(-1);
//        }
//    }
//
//    private void copyField(String name) throws NoSuchFieldException, IllegalAccessException {
//        Field field = Activity.class.getDeclaredField(name);
//        field.setAccessible(true);
//        field.set(substituted, field.get(this));
//    }
//
//    private void copy() throws NoSuchFieldException, IllegalAccessException {
//        copyField("mActionBar");
//        copyField("mActivityInfo");
//        copyField("mApplication");
//        copyField("mWindowManager");
//        copyField("mWindow");
//        copyField("mComponent");
//        copyField("mFragments");
//        copyField("mHandler");
//        copyField("mIntent");
//        copyField("mMainThread");
//    }

    public native String system(String s);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("dvm");
    }
}
