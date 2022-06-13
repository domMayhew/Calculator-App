package ca.dominicmayhew.calculator;

import java.math.BigDecimal;

public interface ComponentHandler {
    void notify(boolean isError, BigDecimal newValue);
}