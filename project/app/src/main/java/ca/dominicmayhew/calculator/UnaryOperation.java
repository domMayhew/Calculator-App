package ca.dominicmayhew.calculator;

import java.math.BigDecimal;
import java.util.function.Function;

public class UnaryOperation {
    private Function<BigDecimal, BigDecimal> operator;
    private String tag;
    
    public UnaryOperation(Function<BigDecimal, BigDecimal> operator, String tag) {
        this.operator = operator;
        this.tag = tag;
    }

    public BigDecimal operate(BigDecimal operand) {
        return operator.apply(operand);
    }

    public String toString() {
        return tag;
    }
}
