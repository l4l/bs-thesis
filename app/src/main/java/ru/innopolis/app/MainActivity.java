package ru.innopolis.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
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

        ApkLoader loader;
        try {
            loader = new ApkLoader(this, app);
            loader.repack();
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            return;
        }

        try {
            substituted = loader.createClass(app + ".MainActivity");//todo: grab from manifest
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
            System.exit(-1);
        }

        assert substituted != null;
        ApkLoader.invoke(substituted, "f");
    }

    public native String system(String s);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("dvm");
    }
}
