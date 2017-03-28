package ru.innopolis.dummy;

import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        ((TextView) findViewById(R.id.phone_num)).setText("Your phone number is: " + tm.getLine1Number());
        Log.d(getClass().getCanonicalName(), "In onCreate-function");
    }

    public void f() {
        Log.d(getClass().getCanonicalName(), "In f-function");
    }
}
