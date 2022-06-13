/**
 * @author Dominic Mayhew
 * @version 2.0
 * @date 2022/03/28
 */

package ca.dominicmayhew.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.nevec.rjm.BigDecimalMath;

public class Expression {
    private Component head;
    private Component tail;
    private UnaryOperation unaryOp;
    private boolean isOpen = true;
    private boolean isSub = false;
    private static MathContext mc = new MathContext(110, RoundingMode.HALF_EVEN); // TODO: Try different values of precision.

    protected boolean isOpen() {
        return isOpen;
    }

    /**
     * Creates a new empty expression with the default MathContext.
     */
    public Expression() { }

    private Expression(boolean isSub) {
        this.isSub = isSub;
    }

    /**
     * Used internally to create subexpressions with a unary operator.
     */
    private Expression(UnaryOperation unaryOp) {
        this(true);
        this.unaryOp = unaryOp;
    }

    /**
     * One of the two main interface methods. Expressions are composed by passing individual components as strings to this method.
     * Valid component strings are numbers (can include leading '-'), operators contained in the OperationLibrary, '(' and ')'.
     * If the string is not valid component it is still added, but marked as an error.
     * @param input the string to be added.
     */
    public void append(String input, ComponentHandler handler) {
        Component newComponent = null;
        BinaryOperation binaryOp;
        UnaryOperation unaryOp;

        if (tail == null || !tail.append(input, handler)) { // Tail did not accept input.
            // Open bracket
            if (input.equals("(")) {
                newComponent = new ExpressionComponent(new Expression(true), handler);
            // Close bracket
            } else if (input.equals(")")) {
                close(handler);
            // Pi
            } else if (input.equals("π")) {
                newComponent = new NumberComponent(BigDecimalMath.pi(mc), handler);
            // Number
            } else if (isNumeric(input)) {
                // scalePrec sets the precision of the BigDecimal so precision isn't lost in BigDecimalMath operations
                BigDecimal bd = BigDecimalMath.scalePrec(new BigDecimal(input), mc);
                newComponent = new NumberComponent(bd, handler);
            // Binary Operator
            } else if ((binaryOp = OperationLibrary.getBinaryOperation(input)) != null) {
                newComponent = new BinaryOperationComponent(binaryOp, handler);
            // Unary Operator
            } else if ((unaryOp = OperationLibrary.getUnaryOperation(input)) != null) {
                newComponent = new ExpressionComponent(new Expression(unaryOp), handler);
            // Unsupported Component
            } else {
                newComponent = new UnsupportedComponent(input, handler);
            }

            if (newComponent != null) { // newComponent == null if input == ")"
                append(newComponent);
            }
        }
    }

    public void append(String input) {
        append(input, null);
    }

    /**
     * Called by append(String) to add the created component to the end of this expression.
     * @param component the component to append.
     */
    protected void append(Component component) {
        if (head == null) {
            head = component;
            tail = component;
            component.setPrevious(null); // Forces Components to set their error state. i.e. leading binary operators are an illegal sequence error.
        } else {
            component.setPrevious(tail);
            tail.setNext(component);
            tail = component;
        }
    }

    /**
     * One of the two public methods.
     * Evaluates this expression.
     * @return the value of this expression.
     * @throws ArithmeticException if there is an arithmetic error, such as dividing by zero.
     */
    public BigDecimal evaluate() throws ArithmeticException {
        if (isEmpty()) {
            return null; // TODO: Should be an error?
        }

        onEvaluate();

        // TODO: This comment block.
        // Try block assumes that the sequence of Components is valid.
        // Catch block handles errors arising from an unexpected sequence.
        // An unexpected sequence represents a programming error: they should have already been caught.
        // This block iterates forward through the expression, "resolving" each component in turn.
        // resolveComponent() checks if the next operation has higher priority and will resolve it first if necessary.
        try {
            // Accumulator contains the current computed value and a reference to the next Component (operation) to be processed.
            Component accumulator = new NumberComponent(head.getValue(), null);
            accumulator.setNext(head.getNext());
            // While there is another Component (operation) to be processed.
            while (accumulator.getNext() != null) {
                accumulator = resolveComponent(accumulator);
            }
            // Process unary operation.
            BigDecimal result;
            if (unaryOp != null) {
                result = unaryOp.operate(accumulator.getValue());
            } else {
                result = accumulator.getValue();
            }
            result.round(mc);
            return result;
        } catch (UnsupportedOperationException uoe) {
            // If an illegal sequence was not caught in append().
            throw new RuntimeException("Syntax error: " + uoe.getMessage() + "\n" + this);
        } catch (NullPointerException npe) {
            // If the expression ends unexpectedly and was not caught in append().
            throw new RuntimeException("Syntax error: " + npe.getMessage() + "\n" + this);
        }
    }
    
    // If the next operation has a higher priority, resolve it first.
    // Compute the result of the current operation and return a ValueComponent with the result
    // and a pointer to the next operation to be resolved, or null if there is none.
    // TODO: Come back here.
    private Component resolveComponent(Component accumulator) {
        Component left = accumulator;
        Component operationComponent = accumulator.getNext();
        BinaryOperation operation = operationComponent.getOperation();
        Component right = operationComponent.getNext();
        while (right.getNext() != null && operation.compareTo(right.getNext().getOperation()) < 0) {
            Component temp = new NumberComponent(right.getValue(), null);
            temp.setNext(right.getNext());
            right = resolveComponent(temp); // TODO: Have another look at this. I was losing trailing expressions because of a wonky setNext().
        }
        try {
            accumulator.value = operation.operate(left.getValue(), right.getValue());
        } catch (ArithmeticException e) {
            operationComponent.setState(Component.State.ARITHMETIC); // Causes operations like x / 0 to have the operator set to an error. TODO: This is not a component handling it's own state.
            throw e;
        }
        accumulator.setNext(right.getNext());
        return accumulator;
    }

    protected void onEvaluate() {
        // Notify all components that an evaluation is being made. This causes components that change state on evaluation (i.e. Open SubExpressions and Trailing Binaries) to do so.
        // This happens before (and separately from) the actual evaluation so that these components will have their state set even if evaluation is cut short by an error being thrown.
        Component temp = head;
        while (temp != null) {
            temp.onEvaluate();
            temp = temp.getNext();
        }
    }


    // TODO: OOOOOH, IDEA! Ok instead of having isSub, you just make open and closed brackets their own components. That way they can handle their own state. OpenBrackets need a next set that is not a closed bracket, and vice versa.
    // TODO: There'd still be an isOpen() function that closed brackets would use to tell if they are an excess closed bracket or not.

    protected void close(ComponentHandler handler) {
        if (isOpen && isSub) {
            isOpen = false;
            // There is probably a better place for this, but close brackets were not being notified if they were part of an empty expression.
            if (isEmpty() && handler != null) {
                handler.notify(true, null);
            }
        } else {
            append(new UnsupportedComponent(")", handler));
        }
    }

    public void delete() {
        if (isSub && !isOpen) { // Delete ")"
            isOpen = true;
            return;
        }

        if (isEmpty()) {
            return;
        }

        if (tail != null && !tail.delete()) {
            tail = tail.getPrevious();
            if (tail != null) {
                tail.setNext(null);
            } else {
                head = null;
            }
        }
    }

    private boolean isNumeric(String input) {
        try {
            new BigDecimal(input);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    // TODO: I guess this can be deleted now?
//    protected boolean containsError() {
//        Component temp = head;
//        while (temp != null) {
//            if (temp.isError()) {
//                return true;
//            }
//            temp = temp.getNext();
//        }
//        return false;
//    }

    public boolean isEmpty() {
        return head == null;
    }

    public String toString() {
        String result = "";
        if (unaryOp != null) {
            result += unaryOp;
        } else if (isSub) {
            result += "(";
        }
        Component curr = head;
        while (curr != null) {
            result += curr;
            curr = curr.getNext();
        }
        if (!isOpen && isSub) {
            result += ")";
        }
        return result;
    }

    // MathContext getter.
    public static MathContext getMathContext() {
        return mc;
    }

    // MathContext setter.
    public static void setMathContext(MathContext newMC) {
        mc = newMC;
    }
}