package ca.dominicmayhew.calculatorapp;

import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Deque;
import java.util.LinkedList;

import ca.dominicmayhew.calculator.ComponentHandler;
import ca.dominicmayhew.calculator.Expression;

class ExpressionBuilder {
    protected static int precision;
    Expression expression;
    Deque<LinearLayout> layoutStack;
    LinearLayout foregroundLayout;
    Deque<ComponentHolder> componentHolders;
    Buffer buffer;
    HorizontalScrollView hsv;
    TextView rightOfCursorTv;
    LinkedList<String> componentStringsRightOfCursor = new LinkedList<>();
    private boolean suppressFling = false;

    protected ExpressionBuilder(HorizontalScrollView hsv, LinearLayout leftOfCursorLayout, TextView rightOfCursorTv) {
        expression = new Expression();
        layoutStack = new LinkedList<>();
        layoutStack.offerFirst(leftOfCursorLayout);
        foregroundLayout = leftOfCursorLayout;
        componentHolders = new LinkedList<>();
        buffer = new Buffer();
        this.hsv = hsv;
        this.rightOfCursorTv = rightOfCursorTv;
    }

    protected void setPrecision(int precision) {
        if (precision < 1 || precision > 100) {
            throw new UnsupportedOperationException("Invalid precision value.");
        }

        ExpressionBuilder.precision = precision;
    }

//    /**
//     * Attaches the provided component to the current layout using the component's own logic.
//     * @param component the component to attach to the current layout.
//     */
//    protected void onComponentAttached(ComponentHolder component) {
//        buffer.flush();
//        componentHolders.offerFirst(component);
//        expression.append(component.getTag(), component.getHandler());
//        hsv.fling(2147483647);
//    }

    protected View getLastAdded() {
        ComponentHolder lastAdded = componentHolders.peekFirst();
        return lastAdded != null ? lastAdded.getView() : null;
    }

    protected void newForeground(LinearLayout newForeground) {
        layoutStack.offerFirst(newForeground);
        foregroundLayout = newForeground;
    }

    protected void closeLayout() {
        if (layoutStack.size() > 1) {
            layoutStack.pollFirst();
            foregroundLayout = layoutStack.peekFirst();
        }
    }

    protected void cursorLeft() {
        suppressFling = true;
        String movedContent = delete();
        if (movedContent != "") {
            rightOfCursorTv.setText(movedContent + rightOfCursorTv.getText());
            componentStringsRightOfCursor.addFirst(movedContent);
        }
    }

    protected void cursorRight() {
        String movedContent = componentStringsRightOfCursor.pollFirst();
        if (componentStringsRightOfCursor.size() == 0) {
            suppressFling = false;
        }
        if (movedContent != null && movedContent != "") {
            rightOfCursorTv.setText(rightOfCursorTv.getText().toString().substring(movedContent.length()));
            if (isNumeric(movedContent)) {
                addDigits(movedContent);
            } else {
                if (movedContent.contains("(")) {
                    new SubExpressionOpener(movedContent).attach(this);
                } else if (movedContent.contains(")")) {
                    new SubExpressionCloser(movedContent).attach(this);
                } else {
                    new ComponentHolder(movedContent).attach(this);
                }
            }
        }
    }

    // TODO: This might take a bit of work...
//    protected boolean acceptingNegative() {
//        ComponentHolder lastAdded = componentHolders.peekFirst();
//        if (buffer.content.length() != 0) { // Digits are already in the buffer.
//            return false;
//        } else if (lastAdded == null) { // Expression is empty.
//            return true;
//        } else { // Expression has contents but buffer is empty.
//            return (!isNumeric(lastAdded.getTag()) && !(lastAdded instanceof SubExpressionCloser));
//        }
//    }

    /**
     * Deletes the most recently added component.
     * Uses the ComponentHolder's own logic to determine what the current foreground layout is.
     */
    protected String delete() {
        String deleted = "";
        if (!buffer.isEmpty()) {
            deleted = buffer.deleteDigit();
        }
        // Buffer was empty, delete not yet consumed.
        else if (convertLastAddedToBuffer()) {
            // lastAdded was numeric and was removed as a component and from the expression and its contents were placed in the buffer.
            deleted = buffer.deleteDigit();
        }
        // Buffer was empty and lastAdded was not numeric, delete not yet consumed.
        else if (componentHolders.size() > 0){
            ComponentHolder lastAdded = componentHolders.pollFirst();
            lastAdded.onDestroy(this);
            foregroundLayout.removeView(lastAdded.getView()); // onDestroy may change the value of foregroundLayout and must come before foregroundLayout.removeView().
            expression.delete();
            deleted = lastAdded.getTag();
        }

        if (buffer.isEmpty()) {
            convertLastAddedToBuffer(); // Ensure that any trailing digits are in the buffer.
        }
        return deleted;
    }

    protected boolean convertLastAddedToBuffer() {
        // If new lastAdded is numeric, remove from expression and add to buffer.
        ComponentHolder lastAdded = componentHolders.peekFirst();
        if (lastAdded != null && isNumeric(lastAdded.getTag())) {
            componentHolders.pollFirst();
            lastAdded.onDestroy(this);
            foregroundLayout.removeView(lastAdded.getView());
            buffer.addDigits(lastAdded.getTag());
            expression.delete();
            return true; // request was completed.
        } else {
            return false; // request was not completed.
        }
    }

//    protected void clear() {
//        expression = new Expression();
//        foregroundLayout = layoutStack.peekFirst();
//        foregroundLayout.removeAllViews();
//        layoutStack = new LinkedList<>();
//        layoutStack.push(foregroundLayout);
//        componentHolders = new LinkedList<>();
//        buffer = new Buffer();
//    }

    private boolean isNumeric(String input) {
        int index = 0;
        int len = input.length();
//        if (input.charAt(0) == '-') { // Allow for leading "-".
//            index++;
//        }
        while (index < len) {
            char ch = input.charAt(index);
            if (!Character.isDigit(ch) && ch != '.') {
                return false;
            }
            index++;
        }
        return true;
    }

    protected void addDigits(String digits) {
        if (buffer.content.length() == 0) {
            convertLastAddedToBuffer();
        }
        buffer.addDigits(digits);
        if (!suppressFling) {
            hsv.fling(2147483647);
            // Schedule another fling for delay because if a fling is already in process, the first fling won't be performed.
            hsv.postDelayed(new Runnable() {
                public void run() {
                    hsv.fling(2147483647);
                }
            }, 20);
        }
    }

    protected void addDigits(BigDecimal bd) {
        addDigits(getStringFromBd(bd, precision)); // TODO: How to not concatenate the precision here?
    }

    protected BigDecimal getValue() {
        while (componentStringsRightOfCursor.size() != 0) {
            cursorRight();
        }
        buffer.flush();
        if (expression.isEmpty()) {
            return null; // TODO: Fix this.
        }
        return expression.evaluate();
    }

    protected String getStringValue() {
        return getStringFromBd(getValue(), precision);
    }

    protected String getStringValueLong() {
        return getStringFromBd(getValue());
    }

    protected LinearLayout getForeground() {
        return foregroundLayout;
    }

    protected void addToBack(ComponentHolder component, View view) {

        buffer.flush();
        foregroundLayout.addView(view);
        componentHolders.offerFirst(component);
        expression.append(component.getTag(), component.getHandler());
        if (!suppressFling) {
            hsv.fling(2147483647);
            // Schedule another fling for delay because if a fling is already in process, the first fling won't be performed.
            hsv.postDelayed(new Runnable() {
                public void run() {
                    hsv.fling(2147483647);
                }
            }, 20);
        }
    }

    protected void removeFromBack(View view) {
        foregroundLayout.removeView(view);
    }

    protected void hideAllValueBraces() {
        for (ComponentHolder holder : componentHolders) {
            holder.hideValueBrace();
        }
    }

    public String toString() {
        String str = "";
        for (ComponentHolder holder : componentHolders) {
            str = holder + str;
        }
        return str;
    }

    private class Buffer {
        private String content = "";
        private View view;
        private TextView tv;

        private Buffer() {
            view = new ComponentHolder("").getView();
            tv = view.findViewById(R.id.contentTv);
        }

        protected void addDigits(String digit) {
            if (content.length() == 0) {
                ExpressionBuilder.this.foregroundLayout.addView(view);
            }
            content += digit;
            tv.setText(content);
        }

        protected String deleteDigit() {
            String deleted = "";
            if (content.length() > 0) {
                deleted = content.substring(content.length() - 1);
                content = content.substring(0, content.length() - 1);
                tv.setText(content);
            }
            if (content.length() == 0) {
                foregroundLayout.removeView(view);
            }
            return deleted;
        }

        protected boolean isEmpty() {
            return content.length() == 0;
        }

        protected void flush() {
            if (content.length() != 0) {
                ExpressionBuilder.this.removeFromBack(view);
                String temp = content;
                content = ""; // This must happen before .attach() or an endless loop will occur.
                new ComponentHolder(temp).attach(ExpressionBuilder.this);
            }
        }
    }

    /**
     * Returns a string for this BigDecimal with the number of digits specified by this object's resultMathContext.
     * Removes trailing zeros for decimal values (including values using scientific notation).
     * <p>
     * Following BigDecimal.toString(), the returned String has as many digits as the precision,
     * plus an Exponent if the number is too large to be represented in that many digits
     * or if the exponent is less than -6.
     *
     * @param bd the BigDecimal to convert to a String
     * @return a String representing this BigDecimal.
     */
    protected static String getStringFromBd(BigDecimal bd, int precision) {
        // Check for valid input and handle special case: zero.
        if (precision < 1 || precision > 100) {
            throw new UnsupportedOperationException("Invalid value for precision.");
        } else if (bd == null) {
            return "";
        } else if (bd.signum() == 0) { // handle zero
            return "0";
        }

        bd.round(new MathContext(precision, RoundingMode.HALF_UP));
        String str = bd.toString(); // Get string with trailing zeros and all precision digits.
        int endIndex;

        // Separate exponent string, set endIndex.
        String exponentString;
        if ((endIndex = str.indexOf("E")) >= 0) {
            exponentString = str.substring(endIndex);
        } else {
            exponentString = "";
            endIndex = str.length();
        }
        // endIndex points to one index greater than the last precision digit.

        // Trim to specified precision.
        int maxLen = str.contains(".") ? precision + 1 : precision; // Account for ".".
        endIndex = min(endIndex, maxLen);

        // Remove trailing zeros if value is a decimal (e.g. "2.430000", or "1.000+E15").
        if (str.contains(".")) { // Remove trailing zeros only if number is a decimal value.
            while (str.charAt(--endIndex) == '0') { // Remove zeros. Uses prefix decrement because endIndex starts at one greater than the last index.
                ;
            }
            if (str.charAt(endIndex) == '.') { // Remove trailing decimal. (e.g. "1.0000" became "1.")
                --endIndex;
            }
            // Move endIndex to one greater than the last digit to include.
            ++endIndex;
        }
        return str.substring(0, endIndex) + exponentString;
    }

    protected static String getStringFromBd(BigDecimal bd) {
        return getStringFromBd(bd, 100);
    }

    private static int min(int left, int right) {
        return left < right ? left : right;
    }
}