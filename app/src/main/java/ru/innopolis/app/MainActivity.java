package ru.innopolis.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final int UNINSTALL_CODE = 123;
    private String signed;
    Vector<ApplicationInfo> infos;
    private int selectedApp = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        listInstalledApps();
        Button b = (Button)findViewById(R.id.button);
        b.setOnClickListener(view -> {
            if (selectedApp != -1) {
                ListView v = (ListView) findViewById(R.id.listView);
                repack((String) v.getAdapter().getItem(selectedApp));
                v.getChildAt(selectedApp).setBackgroundColor(v.getDrawingCacheBackgroundColor());
            } else {
                Toast.makeText(this, "Select the app!", 3);
            }
            selectedApp = -1;
        });
        ListView v = (ListView) findViewById(R.id.listView2);
        v.setAdapter(new ArrayAdapter<String>(this,
                R.layout.app_row, new String[]{"TelephonyManager.getDeviceId"}));
    }

    void listInstalledApps() {
        ListView view = (ListView) findViewById(R.id.listView);
//        view.setOnItemClickListener(new );
        infos = new Vector<>();
        List<ApplicationInfo> apps = getPackageManager()
                .getInstalledApplications(PackageManager.GET_META_DATA);

        List<String> strings = new ArrayList<>(apps.size());
        for (ApplicationInfo info: apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 1) continue;
            strings.add(info.packageName);
            infos.add(info);
        }

        view.setAdapter(new ArrayAdapter<String>(this,
                R.layout.app_row, strings.toArray(new String[strings.size()])));
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                v.setBackgroundColor(Color.WHITE);
                if (selectedApp != -1)
                    view.getChildAt(selectedApp)
                        .setBackgroundColor(view.getDrawingCacheBackgroundColor());
                selectedApp = position;
            }
        });
    }

    void repack(String app) {
//        final String app = "ru.innopolis.dummy";

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

    public native String system(String s);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("dvm");
    }
}
