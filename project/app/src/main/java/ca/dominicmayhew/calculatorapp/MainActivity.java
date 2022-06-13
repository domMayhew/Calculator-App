package ca.dominicmayhew.calculatorapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.nevec.rjm.BigDecimalMath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Scanner;

import ca.dominicmayhew.calculator.OperationLibrary;

public class MainActivity extends AppCompatActivity {
    // TODO: Move these to values/strings.xml
    private final static String HISTORY_FILE_KEY = "ca.dominicmayhew.historyFileKey";
    protected final static String EXPRESSION_EXTRA_KEY = "ca.dominicmayhew.expressionExtraKey";
    protected final static String HISTORY_LIST_EXTRA_KEY = "ca.dominicmayhew.historyListExtraKey";
    protected final static String ACTION_CLEAR_HISTORY = "ca.dominicmayhew.actionClearHistory";
    protected final static String PREFERENCES_KEY = "ca.dominicmayhew.preferencesKey";
    protected final static String PRECISION_INT_KEY = "ca.dominicmayhew.precisionIntKey";
    protected final static String USE_DEGREES_BOOLEAN_KEY = "ca.dominicmayhew.trigModeBooleanKey";
    protected final static String HAPTIC_BOOLEAN_KEY = "ca.dominicmayhew.hapticBooleanKey";
    protected final static String VALUE_EXTENSION_BOOLEAN_KEY = "ca.dominicmayhew.valueExtensionBooleanKey";
    protected final static String FIRST_USE = "ca.dominicmayhew.firstUse";

    private ArrayList<String> history;
    static MathContext mc = new MathContext(20, RoundingMode.HALF_UP);
    ConstraintLayout displayCl; // Cl for "Constraint Layout"
    LinearLayout expressionLeftLl; // Hl for "Horizontal Layout"
    TextView rightOfCursorTv;
    TextView resultTv; // Tv for "Text View"
    ExpressionBuilder builder;
    BigDecimal memory;
    boolean completed = false;
    HorizontalScrollView hsv;
    ClearHistoryReceiver clearHistoryReceiver;
    boolean haptic;
    boolean valueExtension;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayCl = findViewById(R.id.displayCl);
        expressionLeftLl = displayCl.findViewById(R.id.expressionLeftLl);
        rightOfCursorTv = displayCl.findViewById(R.id.rightOfCursorTv);
        resultTv = displayCl.findViewById(R.id.resultTv);
        resultTv.setMovementMethod(new ScrollingMovementMethod());
        resultTv.setOnClickListener(resultClickListener);
        hsv = findViewById(R.id.expressionHsv);

        ComponentHolder.setRoot(expressionLeftLl);
        String expr;
        Intent i = getIntent();
        expr = i.getStringExtra(EXPRESSION_EXTRA_KEY);
        if (savedInstanceState != null) {
            expr = savedInstanceState.getString(EXPRESSION_EXTRA_KEY, "");
        } else if (expr == null) {
            expr = "";
        }
        if (expr.indexOf(" =") >= 0) {
            expr = expr.substring(0, expr.indexOf(" ="));
        }
        builder = buildFromString(expr, hsv, expressionLeftLl, rightOfCursorTv);
        setCompleted(false, null);
        getHistory();
        clearHistoryReceiver = new ClearHistoryReceiver();
        IntentFilter clearHistoryFilter = new IntentFilter();
        clearHistoryFilter.addAction(MainActivity.ACTION_CLEAR_HISTORY);
        this.registerReceiver(clearHistoryReceiver, clearHistoryFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE);
        if (prefs.getBoolean(FIRST_USE, true)) {
            prefs.edit().putBoolean(FIRST_USE, false).commit();
            startUserGuide(null);
        }
        builder.setPrecision(prefs.getInt(PRECISION_INT_KEY, 10));
        setTrigMode(prefs.getBoolean(USE_DEGREES_BOOLEAN_KEY, true));
        haptic = prefs.getBoolean(HAPTIC_BOOLEAN_KEY, true);
        valueExtension = prefs.getBoolean(VALUE_EXTENSION_BOOLEAN_KEY, true);
        setMenuVisibility(View.GONE);
        setMemoryVisibility(View.GONE);
        setCompleted(false, null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outBundle) {
        super.onSaveInstanceState(outBundle);
        outBundle.putString(EXPRESSION_EXTRA_KEY, builder.toString());
        saveHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(clearHistoryReceiver);
    }

    protected void clearHistory() {
        history.clear();
        saveHistory();
    }

    private OnClickListener resultClickListener = new OnClickListener() {
        long lastClickTime = 0;
        boolean enabled = valueExtension;

        public void onClick(View v) {
            buttonPressPreamble(v);
            if (!valueExtension) {
                return;
            }
            if (!completed) {
                return;
            } else if (System.currentTimeMillis() - lastClickTime < 200) {
                try {
                    resultTv.setText(builder.getStringValueLong());
                    builder.hideAllValueBraces();
                } catch (Exception e) {
                    errorAnimation("Cannot compute the value of this expression.", displayCl);
                }
            } else {
                lastClickTime = System.currentTimeMillis();
            }
        }
    };

    public void startHistory(View v) {
        Intent i = new Intent(this, HistoryActivity.class);
        i.putStringArrayListExtra(HISTORY_LIST_EXTRA_KEY, history);
        startActivity(i);
    }

    public void startSettings(View v) {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
    }

    public void startUserGuide(View v) {
        Intent i = new Intent(this, UserGuide.class);
        startActivity(i);
    }

    public void sendFeedback(View v) {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:")); // ensures only email apps handle this intent.
        i.putExtra(Intent.EXTRA_EMAIL, "dominic.j.mayhew@gmail.com");
        i.putExtra(Intent.EXTRA_SUBJECT, "Feedback on Calculator App");
        startActivity(i);
    }

    public void digitListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        builder.addDigits(v.getTag().toString());
    }

    private void buttonHaptic(View v) {
        if (haptic) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void cursorLeft(View v) {
        buttonPressPreamble(v);
        setCompleted(false, null);
        builder.cursorLeft();
    }

    public void cursorRight(View v) {
        buttonPressPreamble(v);
        setCompleted(false, null);
        builder.cursorRight();
    }

    public void setTrigMode(boolean newUseDegrees) {
        OperationLibrary.useDegrees = newUseDegrees;
        getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE).edit().putBoolean(USE_DEGREES_BOOLEAN_KEY, OperationLibrary.useDegrees).commit();
        ((TextView) findViewById(R.id.trigModeBtn)).setText(OperationLibrary.useDegrees ? "DEG" : "RAD");
        try {
            builder.getValue();
        } catch (Exception e) {
            // Do nothing. Do not trigger an errorAnimation.
        }
    }

    public void toggleTrigMode(View v) {
        buttonPressPreamble(v);
        setTrigMode (!OperationLibrary.useDegrees);
        setCompleted(false, null);
    }

    private void getHistory() {
        history = new ArrayList<>();
        File historyFile;
        try {
            historyFile = new File(getFilesDir(), HISTORY_FILE_KEY);
            Scanner scanner = new Scanner(historyFile);
            while (scanner.hasNextLine()) {
                history.add(scanner.nextLine());
            }
            scanner.close();
            Log.i("GET_HISTORY", history.size() + " strings read from disk.");
        } catch (FileNotFoundException e) {
            Log.i("GET_HISTORY", "File not found. History is empty.");
        }
    }

    private void saveHistory() {
        try {
            File historyFile = new File(getFilesDir(), HISTORY_FILE_KEY);
            FileWriter writer = new FileWriter(historyFile);
            for (String expr : history) {
                writer.write(expr + "\n");
            }
            writer.close();
            Log.i("SAVE_HISTORY", history.size() + " Strings written saved to disk.");
        } catch (FileNotFoundException e) {
            Log.i("SAVE_HISTORY", "File not found exception. History was not written.\n\t" + e.getMessage());
        } catch (IOException e) {
            Log.i("SAVE_HISTORY", "IOException. History may not have been written.\n\t" + e.getMessage());
        }
    }


    public void binaryOperatorListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new ComponentHolder(v.getTag().toString()).attach(builder);
    }

    // TODO: This might take a bit of work...
//    public void minusListener(View v) {
//        buttonHaptic(v);
//        if (completed) {
//            clearListener(null);
//        }
//        if (builder.acceptingNegative()) {
//            builder.addDigits("-");
//        } else {
//            new ComponentHolder("-").attach(builder);
//        }
//    }

    public void squaredListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new ComponentHolder("^").attach(builder);
        new ComponentHolder("2").attach(builder);
    }

    public void unaryOperatorListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new SubExpressionOpener(v.getTag().toString()).attach(builder);
    }

    public void generalListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new ComponentHolder(v.getTag().toString()).attach(builder);
    }

    public void openBracketListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new SubExpressionOpener(v.getTag().toString()).attach(builder);
    }

    public void closeBracketListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            clearListener(null);
        }
        new SubExpressionCloser(v.getTag().toString()).attach(builder);
    }

    public void deleteListener(View v) {
        buttonPressPreamble(v);
        builder.delete();
        setCompleted(false, null);
    }

    public void clearListener(View v) {
        if (v != null) {
            buttonPressPreamble(v);
        }
        expressionLeftLl.removeAllViews(); // TODO: Remove buffer.clear()?
        builder = new ExpressionBuilder(hsv, expressionLeftLl, rightOfCursorTv);
        resultTv.setText("");
        setCompleted(false, null);
    }

    public void equalsListener(View v) {
        buttonPressPreamble(v);
        if (completed) {
            return;
        }
        try {
            String value = builder.getStringValue();
            builder.hideAllValueBraces(); // Must come after .getStringValue() because .getStringValue() will cause the braces to reappear.
            setCompleted(true, value);
            history.add(0, builder.toString() + " = " + value);
        } catch (Exception e) {
            errorAnimation("Could not compute.", displayCl); // TODO: Better toast messages.
            Log.i("EQUALS", "Exception thrown: " + e.getMessage());
        }
    }

    private void buttonPressPreamble(View v) {
        buttonHaptic(v);
        setMemoryVisibility(View.GONE);
        setMenuVisibility(View.GONE);
    }


    private int menuVisibility = View.GONE;

    public void toggleMenu(View v) {
        buttonHaptic(v);
        setMemoryVisibility(View.GONE);
        setMenuVisibility(menuVisibility == View.GONE ? View.VISIBLE : View.GONE);
    }

    public void setMenuVisibility(int visibility) {
        menuVisibility = visibility;
        findViewById(R.id.menuSettingsTv).setVisibility(visibility);
        findViewById(R.id.menuHistoryTv).setVisibility(visibility);
        findViewById(R.id.menuUserGuideTv).setVisibility(visibility);
        findViewById(R.id.menuFeedbackTv).setVisibility(visibility);
    }

    int memoryVisibility = View.GONE;

    public void toggleMemory(View v) {
        buttonHaptic(v);
        setMenuVisibility(View.GONE);
        setMemoryVisibility(memoryVisibility == View.GONE ? View.VISIBLE : View.GONE);
    }

    private void setMemoryVisibility(int visibility) {
        memoryVisibility = visibility;
        findViewById(R.id.memoryStore).setVisibility(visibility);
        findViewById(R.id.memoryRecall).setVisibility(visibility);
        findViewById(R.id.memoryPlus).setVisibility(visibility);
        findViewById(R.id.memoryMinus).setVisibility(visibility);
    }

    /**
     * OnClickListener for the memory store ("MS") button.
     * If the current expression is complete, stores its value into memory for later user use.
     *
     * @param v the view that was clicked (i.e. the "MS" button).
     */
    public void memoryStoreListener(View v) {
        buttonHaptic(v);
        try {
            memory = builder.getValue();
        } catch (ArithmeticException ae) { // Expression could not be evaluated (e.g. tried to take the square root of a negative).
            errorAnimation("Cannot store in memory because an arithmetic error exists.", displayCl); // Should not occur (completed would not be true if current expression contained errors).
            Log.w("MEMORY_STORE", "completed == true, but parsing threw an exception. This is a programming error.\n" + ae);
        } catch (Exception e) { // Prevents application crash.
            errorAnimation("Cannot store in memory because an unknown error exists.", displayCl);
            Log.w("MEMORY_STORE", "completed == true, but parsing threw an Exception. This is a programming error.\n" + e);
        }
    }

    /**
     * OnClickListener for the memory recall ("MR") button.
     * Appends the value currently stored in memory to the current expression string.
     *
     * @param v the view that was clicked (i.e. the "MR" button).
     */
    public void memoryRecallListener(View v) {
        buttonHaptic(v);
        if (memory == null) {
            errorAnimation("There is nothing stored in memory.", displayCl);
            return;
        }

        if (completed) {
            clearListener(null);
        }

        builder.addDigits(memory);
    }


    /**
     * The OnClickListener for the memory plus ("M+") button.
     * Adds the value of the current expression to the value stored in memory,
     * or, if there is nothing stored in memory, sets memory to the value of the current expression.
     *
     * @param v the view that was clicked (i.e. the "M+" button).
     */
    public void memoryPlusListener(View v) {
        buttonHaptic(v);
        if (memory == null) {
            errorAnimation(getResources().getString(R.string.memoryEmpty), displayCl);
        } else {
            try {
                memory = BigDecimalMath.addRound(memory, builder.getValue());
            } catch (ArithmeticException ae) { // Expression could not be evaluated (e.g. tried to take the square root of a negative).
                errorAnimation("Cannot add to memory because an arithmetic error exists.", displayCl); // Should not occur.
                Log.w("MEMORY_ADD", "completed == true, but parsing threw an exception. This is a programming error.\n" + ae);
            } catch (Exception e) { // Prevents application crash.
                errorAnimation("Cannot add to memory because an unknown error exists.", displayCl);
                Log.w("MEMORY_ADD", "completed == true, but parsing threw an Exception. This is a programming error.\n" + e);
            }
        }
    }

    /**
     * The OnClickListener for the memory minus ("M-") button.
     * Subtracts the value of the current expression from the value stored in memory,
     * or, if there is nothing stored in memory, sets memory to the negation of the value of the current expression.
     *
     * @param v the view that was clicked (i.e. the "M-" button).
     */
    public void memoryMinusListener(View v) {
        buttonHaptic(v);
        if (memory == null) {
            errorAnimation(getResources().getString(R.string.memoryEmpty), displayCl);
        } else {
            try {
                memory = memory.subtract(builder.getValue());
            } catch (ArithmeticException ae) { // Evaluation error
                errorAnimation("Cannot subtract from memory because an arithmetic error exists.", displayCl); // Should not occur.
                Log.w("MEMORY_MINUS", "completed == true, but parsing threw an exception. This is a programming error.");
            } catch (Exception e) { // Prevents system crash
                errorAnimation("Cannot add to memory because an unknown error exists.", displayCl);
                Log.w("MEMORY_MINUS", "completed == true, but parsing threw an Exception. This is a programming error.\n" + e);
            }
        }
    }

    /**
     * Sets the completed variable and updates the display to emphasize either the expression or the result.
     *
     * @param isCompleted if the expression has been evaluated successfully (i.e. user pressed "=").
     */
    private void setCompleted(boolean isCompleted, String value) {
        completed = isCompleted;

        // Set up.
        View foreground = isCompleted ? resultTv : hsv;
        View background = isCompleted ? hsv : resultTv;
        float foregroundAlpha = 1f;
        float backgroundAlpha = 0.6f;

        // Set text color
        foreground.setAlpha(foregroundAlpha);
        background.setAlpha(backgroundAlpha);

        // Set Layout Params
        ConstraintLayout.LayoutParams foregroundLayoutParams = (ConstraintLayout.LayoutParams) foreground.getLayoutParams();
        ConstraintLayout.LayoutParams backgroundLayoutParams = (ConstraintLayout.LayoutParams) background.getLayoutParams();
        foregroundLayoutParams.verticalWeight = 2;
        backgroundLayoutParams.verticalWeight = isCompleted ? 1 : 0;
        foreground.setLayoutParams(foregroundLayoutParams);
        background.setLayoutParams(backgroundLayoutParams);

        if (isCompleted) {
            resultTv.setText(value);
        } else {
            resultTv.setText("");
        }
    }

    /**
     * Creates an animation to alert the user that the expression could not be evaluated.
     * Makes the expression display part of the screen flash red and shows a toast message.
     *
     * @param message the text to be displayed in the toast message.
     */
    public void errorAnimation(String message, View view) {
        resultTv.setText("");
        int normalColor = getResources().getColor(isDarkModeOn() ? R.color.black : R.color.white);
        int errorColor = getResources().getColor(R.color.error);
        if (view.getBackground() == null) {
            // The first time this is called the background of rootDisplayLayout may be null. It must have a value for the animation to work.
            view.setBackground(new ColorDrawable(errorColor));
        }
        ObjectAnimator oa = ObjectAnimator.ofArgb(view.getBackground(), "tint", errorColor, normalColor);
        oa.setDuration(500); // 500 ms
        oa.setInterpolator(new android.view.animation.DecelerateInterpolator(0.75f));
        oa.start();
        if (haptic) {
            errorVibrationEffect();
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void errorVibrationEffect() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            VibrationEffect vibe = VibrationEffect.createOneShot(1000, VibrationEffect.EFFECT_DOUBLE_CLICK);
            vibrator.cancel();
            vibrator.vibrate(vibe);
        } else {
            vibrator.cancel();
            vibrator.vibrate(1000);
        }
    }

    /**
     * Returns true if the host phone is in dark mode.
     *
     * @return true if the host phone is in dark mode.
     */
    private boolean isDarkModeOn() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    protected static ExpressionBuilder buildFromString(String input, HorizontalScrollView hsv, LinearLayout root, TextView rightOfCursorTv) {
        ExpressionBuilder builder = new ExpressionBuilder(hsv, root, rightOfCursorTv);

        boolean acceptingNegative = true;
        int start = 0;
        int end = 0;
        int len = input.length();
        while (start < len) {
            // Process number.
            while (end < len && isNumeric(input.charAt(end), acceptingNegative)) {
                end++;
                acceptingNegative = false;
            }
            if (end > start) {
                new ComponentHolder(input.substring(start, end)).attach(builder); // Number.
                start = end;
            }
            // Process string.
            while (end < len && !isNumeric(input.charAt(end), acceptingNegative)) {
                end++;
                if (OperationLibrary.getBinaryOperation(input.substring(start, end)) != null) {
                    new ComponentHolder(input.substring(start, end)).attach(builder);
                    start = end;
                    acceptingNegative = true;
                } else if (OperationLibrary.getUnaryOperation(input.substring(start, end)) != null) {
                    new SubExpressionOpener(input.substring(start, end)).attach(builder);
                    start = end;
                    acceptingNegative = true;
                } else if (input.substring(start, end).equals("(")) {
                    new SubExpressionOpener(input.substring(start, end)).attach(builder);
                    start = end;
                    acceptingNegative = true;
                } else if (input.substring(start, end).equals(")")) {
                    new SubExpressionCloser(input.substring(start, end)).attach(builder);
                    start = end;
                    acceptingNegative = false;
                } else if (input.substring(start, end).equals("π")) {
                    new ComponentHolder(input.substring(start, end)).attach(builder);
                    start = end;
                    acceptingNegative = false;
                }
            }
            if (start < end) { // Unrecognized symbol.
                new ComponentHolder(input.substring(start, end)).attach(builder);
            }
        }
        return builder;
    }

    private static boolean isNumeric(char ch, boolean acceptingNegative) {
        if (acceptingNegative) {
            return (ch >= '0' && ch <= '9' || ch == '.' || ch == '-');
        } else {
            return (ch >= '0' && ch <= '9' || ch == '.');
        }
    }
}