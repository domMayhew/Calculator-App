package ca.dominicmayhew.calculatorapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {
    SharedPreferences prefs;
    EditText precisionEt;
    RadioButton degreesRb;
    RadioButton radiansRb;
    Switch hapticSwitch;
    Switch valueExpansionSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        precisionEt = findViewById(R.id.settingsPrecisionEt);
        precisionEt.setOnEditorActionListener(this::precisionListener);

        degreesRb = findViewById(R.id.degreesRb);
        radiansRb = findViewById(R.id.radiansRb);
        // RadioButton listeners declared in XML.

        hapticSwitch = findViewById(R.id.settingsHapticSwitch);
        hapticSwitch.setOnCheckedChangeListener(this::hapticListener);
        valueExpansionSwitch = findViewById(R.id.settingsValueExpansionSwitch);
        valueExpansionSwitch.setOnCheckedChangeListener(this::valueExpansionListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        prefs = getSharedPreferences(MainActivity.PREFERENCES_KEY, Context.MODE_PRIVATE);
        precisionEt.setText(Integer.toString(prefs.getInt(MainActivity.PRECISION_INT_KEY, 10))); // TODO: Change to a value resource.
        if (prefs.getBoolean(MainActivity.USE_DEGREES_BOOLEAN_KEY, true) == true) {
            degreesRb.setChecked(true);
        } else {
            radiansRb.setChecked(true);
        }
        hapticSwitch.setChecked(prefs.getBoolean(MainActivity.HAPTIC_BOOLEAN_KEY, true));
        valueExpansionSwitch.setChecked(prefs.getBoolean(MainActivity.VALUE_EXTENSION_BOOLEAN_KEY, true));
    }

    // Returning true causes the ime action to stop propagating, and imeOptions="actionDone" does not receive the action and does not hide the keyboard.
    public boolean precisionListener(TextView tv, int actionId, KeyEvent ke) {
        try {
            int precision = Integer.parseInt(tv.getText().toString());
            if (precision < 1 || precision > 100) {
                Toast.makeText(this, "Please enter an integer between 1 and 100.", Toast.LENGTH_SHORT).show();
                return true; // Prevents imeOptions="actionDone" from hiding the keyboard.
            } else {
                prefs.edit().putInt(MainActivity.PRECISION_INT_KEY, precision).apply();
                tv.clearFocus(); // Removes cursor from edit text.
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter an integer between 1 and 100.", Toast.LENGTH_SHORT).show();
        }
        return false; // Causes imeOptions="actionDone" to hide the keyboard.
    }

    public void trigButtonListener(View v) {
        switch (v.getId()) {
            case R.id.degreesRb:
                prefs.edit().putBoolean(MainActivity.USE_DEGREES_BOOLEAN_KEY, true).commit();
                break;
            case R.id.radiansRb:
                prefs.edit().putBoolean(MainActivity.USE_DEGREES_BOOLEAN_KEY, false).commit();
                break;
        }
    }

    public void hapticListener(CompoundButton cb, boolean isChecked) {
        prefs.edit().putBoolean(MainActivity.HAPTIC_BOOLEAN_KEY, isChecked).commit();
    }

    public void valueExpansionListener(CompoundButton cb, boolean isChecked) {
        prefs.edit().putBoolean(MainActivity.VALUE_EXTENSION_BOOLEAN_KEY, isChecked).commit();
    }
}