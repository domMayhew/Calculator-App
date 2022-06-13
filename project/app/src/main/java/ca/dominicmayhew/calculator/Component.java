package ca.dominicmayhew.calculator;

import org.nevec.rjm.BigDecimalMath;

import java.math.BigDecimal;
import java.math.MathContext;

abstract class Component {
    protected Component previous;
    protected Component next;
    protected State errorState = State.NONE;
    protected ComponentHandler handler;
    protected Type type;
    protected BigDecimal value;

    protected Component(ComponentHandler handler) {
        this.handler = handler;
    }

    public enum Type { NUMBER, BINARY_OPERATION, EXPRESSION, UNSUPPORTED }
    public enum State { NONE, ILLEGAL_SEQUENCE, TRAILING_BINARY, ARITHMETIC, OPEN_SUB, EMPTY_EXPRESSION, UNSUPPORTED_COMPONENT }

    protected abstract void setPrevious(Component component);

    protected void setNext(Component component) {
        next = component;
    }
    
    protected Component getPrevious() {
        return previous;
    }

    protected Component getNext() {
        return next;
    }

    protected void setState(State state) {
        errorState = state;
        if (handler != null) {
            handler.notify(isError(), null);
        }
    }

    protected void onEvaluate() {
        return;
    }

    protected boolean isError() {
        return errorState != State.NONE;
    }

    protected boolean isOpen() { return false; }

    protected BigDecimal getValue() {
        throw new UnsupportedOperationException("getValue() not applicable for component type " + type + ".");
    }

    protected BinaryOperation getOperation() {
        throw new UnsupportedOperationException("getOperation() not applicable for component type " + type + ".");
    }

    protected Expression getExpression() {
        throw new UnsupportedOperationException("getExpression() not applicable for component type " + type + ".");
    }

    protected boolean append(String input, ComponentHandler handler) {
        return false;
    }

    protected boolean delete() {
        return false;
    }

    public abstract String toString();
}

class NumberComponent extends Component {
    protected NumberComponent(BigDecimal value, ComponentHandler handler) {
        super(handler);
        this.value = value;
        this.type = Type.NUMBER;
    }

    @Override
    protected BigDecimal getValue() {
        return value;
    }

    @Override
    protected void setPrevious(Component component) {
        previous = component;
        if (previous != null && (previous.type == Type.NUMBER || previous.type == Type.EXPRESSION)) {
            setState(State.ILLEGAL_SEQUENCE); // TODO: Allow multiple Number/expression
        }
    }

    @Override
    public String toString() {
        if (value.compareTo(BigDecimalMath.pi(new MathContext(value.precision()))) == 0) {
            return "π";
        }
        return value.stripTrailingZeros().toString(); // This returns bad results and is to be avoided.
    }
}

class ExpressionComponent extends Component {
    protected Expression expression;

    protected ExpressionComponent(Expression expression, ComponentHandler handler) {
        super(handler);
        this.expression = expression;
        this.type = Type.EXPRESSION;
    }

    @Override
    protected BigDecimal getValue() {
        if (expression.isOpen()) {
            setState(State.OPEN_SUB);
        } else if (expression.isEmpty()) {
            setState(State.EMPTY_EXPRESSION);
        }
        value = expression.evaluate(); // TODO: No class-level 'value'?
        if (expression.isOpen()) {
            throw new RuntimeException("Missing one or more \")\""); // Could remove this to allow open expression to still return a value.
        }
        if (handler != null) {
            handler.notify(isError(), value); // TODO: This only gets called if expression.evaluate() did not throw an error... Maybe that's proper though, since if it contains an error does not change if the component itself is an error.
        }
        return value;
    }

    @Override
    protected void setPrevious(Component component) {
        previous = component;
        if (previous != null && (previous.type == Type.NUMBER || previous.type == Type.EXPRESSION)) {
            setState(State.ILLEGAL_SEQUENCE);
        }
    }

    @Override
    protected Expression getExpression() {
        return expression;
    }

    @Override
    protected boolean isOpen() { return expression.isOpen(); }

    @Override
    protected boolean isError() {
        return errorState != State.NONE;
    }

    @Override
    protected void onEvaluate() {
        if (expression.isOpen()) {
            setState(State.OPEN_SUB);
        }
        // Causes onEvaluate() to be called on components within this expression.
        // This is necessary because of this sequence during an evaluate() event.
        // 1. Parent expression receives evaluate() event and calls onEvaluate() on all its components (including this one).
        // 2. Parent expression tries to evaluate itself, calling getValue() on its components, but may not complete due to getValue() throwing an exception.
        // If this component does not call onEvaluate() on its expression, the expression may never receive the evaluation event.
        // This does result in onEvaluate() being called multiple times though, so perhaps could be restructured.
        expression.onEvaluate();
    }

    @Override
    protected boolean append(String input, ComponentHandler newComponentHandler) {
        if (expression.isOpen()) {
            expression.append(input, newComponentHandler);
            if (!expression.isOpen()) {
                if (errorState == State.OPEN_SUB || errorState == State.EMPTY_EXPRESSION) {
                    setState(State.NONE);
                }
                try {
                    getValue(); // Notifies the handler. Sets EMPTY_EXPRESSION state if necessary.
                } catch (ArithmeticException e) {
                    if (handler != null) {
                        handler.notify(true, null);
                    }
                } catch (Exception e) {
                    // Exceptions like illegal sequence should have already notified their handlers.
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected boolean delete() {
        if (!expression.isOpen() || !expression.isEmpty()) {
            expression.delete();
            if (handler != null) {
                handler.notify(isError(), null);
            }
            return true; // delete() event was consumed.
        } else {
            return false; // delete() event was not consumed.
        }
    }

    @Override
    public String toString() {
        return expression.toString();
    }
}

class BinaryOperationComponent extends Component {
    protected BinaryOperation operation;

    protected BinaryOperationComponent(BinaryOperation operation, ComponentHandler handler) {
        super(handler);
        this.operation = operation;
        this.type = Type.BINARY_OPERATION;
    }

    @Override
    protected void setPrevious(Component component) {
        previous = component;
        if (previous == null || previous.type == Type.BINARY_OPERATION) {
            setState(State.ILLEGAL_SEQUENCE);
        }
    }

    @Override
    protected void setNext(Component component) {
        super.setNext(component);
        if (errorState == State.TRAILING_BINARY || errorState == State.ARITHMETIC) {
            setState(State.NONE);
        }
    }

    @Override
    protected void onEvaluate() {
        if (next == null) {
            setState(State.TRAILING_BINARY);
        }
    }

//    // TODO: Maybe this logic should be in Expression.evaluate()?
    //   TODO: I think this logic *is* in Expression.evaluate() now and can be deleted here.
//    protected NumberComponent operate(Component left, Component right) throws SyntaxException {
//        try {
//            BigDecimal value = operation.operate(left.getValue(), right.getValue());
//            NumberComponent result = new NumberComponent(value, null);
//            result.setNext(right.getNext());
//            return result;
//        } catch (UnsupportedOperationException uoe) {
//            throw new SyntaxException(uoe.getMessage()); // TODO
//        }
//    }

    @Override
    protected BinaryOperation getOperation() {
        return operation;
    }

    // TODO: Maybe this logic should be in Expression.evaluate()?
    public int compareTo(BinaryOperationComponent otherOpComponent) {
        if (otherOpComponent == null) {
            return operation.getPriority();
        } else {
            return operation.getPriority() - otherOpComponent.getOperation().getPriority();
        }
    }

    @Override
    public String toString() {
        return operation.toString();
    }
}

class UnsupportedComponent extends Component {
    private String string;

    protected UnsupportedComponent(String string, ComponentHandler handler) {
        super(handler);
        this.string = string;
        type = Type.UNSUPPORTED;
        setState(State.UNSUPPORTED_COMPONENT);
    }

    @Override
    protected void setPrevious(Component component) {
        previous = component;
    }

    @Override
    public String toString() {
        return string;
    }
}