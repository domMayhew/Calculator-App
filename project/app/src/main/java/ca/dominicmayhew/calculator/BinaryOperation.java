package ca.dominicmayhew.calculator;

import java.math.BigDecimal;
import java.util.function.BiFunction;

public class BinaryOperation {
    private BiFunction<BigDecimal, BigDecimal, BigDecimal> operator;
    private int priority;
    private String tag;

    public BinaryOperation(BiFunction<BigDecimal, BigDecimal, BigDecimal> operator, int priority, String tag) {
        this.operator = operator;
        this.priority = priority;
        this.tag = tag;
    }

    public BigDecimal operate(BigDecimal left, BigDecimal right) {
        return operator.apply(left, right);
    }

    public int getPriority() {
        return priority;
    }

    public int compareTo(BinaryOperation otherOp) {
        return priority - otherOp.priority;
    }

    public String toString() {
        return tag;
    }
}
