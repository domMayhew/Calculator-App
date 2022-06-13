package ca.dominicmayhew.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;

import org.nevec.rjm.BigDecimalMath;

public class OperationLibrary {
    private static HashMap<String, BinaryOperation> binaryOps = new HashMap<String, BinaryOperation>();
    private static HashMap<String, UnaryOperation> unaryOps = new HashMap<String, UnaryOperation>();
    public static boolean useDegrees;

    // Binary Operations
    static {
        // Add
        binaryOps.put("+", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            return left.add(right);
        }, 0, "+"));
        // Subtract
        binaryOps.put("-", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            return left.subtract(right);
        }, 0, "-"));
        // Multiply '*'
        binaryOps.put("*", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            return left.multiply(right);
        }, 1, "*"));
        // Multiply 'x'
        binaryOps.put("x", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            return left.multiply(right, new MathContext(min(left.precision(), right.precision())));
        }, 1, "x"));
        // Divide '/'
        binaryOps.put("/", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            if (right.equals(new BigDecimal(0))) {
                throw new ArithmeticException("Divide by zero.");
            }
            return BigDecimalMath.divideRound(left, right);
        }, 1, "/"));
        // Divide '÷'
        binaryOps.put("÷", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            if (right.equals(new BigDecimal(0))) {
                throw new ArithmeticException("Divide by zero.");
            }
            return BigDecimalMath.divideRound(left, right);
        }, 1, "÷"));
        // Power '^'
        binaryOps.put("^", new BinaryOperation((BigDecimal left, BigDecimal right) -> {
            BigDecimal result;
            try { // Try integer operation first because BigDecimalMath returns unusual, fractional results.
                int rightAsInt = right.intValueExact();
                result = BigDecimalMath.powRound(left, rightAsInt);
            } catch (ArithmeticException ae) { // right could not be turned into a integer, try decimal operation.
                result = BigDecimalMath.pow(left, right); // Can throw arithmetic exception if base is negative and exponent is fractional.
            }
            return result;
        }, 2, "^"));
    }

    // Unary Operations
    static {
        // sqrt()
        unaryOps.put("sqrt(", new UnaryOperation((BigDecimal operand) -> {
            if (operand.signum() == 0) {
                return operand;
            } else {
                return BigDecimalMath.sqrt(operand);
            }
        }, "sqrt("));
        // √()
        unaryOps.put("√(", new UnaryOperation((BigDecimal operand) -> {
            if (operand.signum() == 0) {
                return operand;
            } else {
                return BigDecimalMath.sqrt(operand);
            }
        }, "√("));
        // Sin
        unaryOps.put("sin(", new UnaryOperation((BigDecimal operand) -> {
            // BigDecimalMath cannot handle values that are equivalent to PI/2 or PI/4 mod 2*PI for this operation.
            // A check is required to prevent these values from being passed to the library method.
            if (useDegrees) {
                operand = convertToRadians(operand);
            }

            if (isZeroMod2Pi(operand)) { // sin(0) = 0
                return BigDecimalMath.scalePrec(new BigDecimal("0"), operand.precision());
            } else if (isFractionOfPi(operand, 1)) { // Sin(PI) = 0
                return BigDecimalMath.scalePrec(new BigDecimal("0"), operand.precision());
            } else if (isFractionOfPi(operand, 2)) { // Sin(PI/2) = 1
                return BigDecimalMath.scalePrec(new BigDecimal("1"), operand.precision());
            } else if (isFractionOfPi(operand, 4)) { // Sin(PI/4) = 1/sqrt(2)
                MathContext mc = new MathContext(operand.precision());
                BigDecimal root2 = BigDecimalMath.sqrt(new BigDecimal("2"), mc);
                return new BigDecimal("1").divide(root2, mc);
            } else {
                return BigDecimalMath.sin(operand);
            }
        }, "sin("));
        // Cos
        unaryOps.put("cos(", new UnaryOperation((BigDecimal operand) -> {
            // BigDecimalMath cannot handle values that are equivalent to PI/2 or PI/4 mod 2*PI for this operation.
            // A check is required to prevent these values from being passed to the library method.
            if (useDegrees) {
                operand = convertToRadians(operand);
            }

            if (isZeroMod2Pi(operand)) { // cos(0) = 1
                return BigDecimalMath.scalePrec(new BigDecimal("1"), operand.precision());
            } else if (isFractionOfPi(operand, 1)) { // cos(PI) = -1
                return BigDecimalMath.scalePrec(new BigDecimal("-1"), operand.precision());
            } else if (isFractionOfPi(operand, 2)) { // cos(PI/2) = 0
                return BigDecimalMath.scalePrec(new BigDecimal("0"), operand.precision());
            } else if (isFractionOfPi(operand, 4)) { // cos(PI/4) = 1/sqrt(2)
                MathContext mc = new MathContext(operand.precision());
                BigDecimal root2 = BigDecimalMath.sqrt(new BigDecimal("2"), mc);
                return new BigDecimal("1").divide(root2, mc);
            } else {
                return BigDecimalMath.cos(operand);
            }
        }, "cos("));
        // Tan
        unaryOps.put("tan(", new UnaryOperation((BigDecimal operand) -> {
            if (useDegrees) {
                operand = convertToRadians(operand);
            }

            if (isZeroMod2Pi(operand)) { // tan(0) = 0
                return BigDecimalMath.scalePrec(new BigDecimal("0"), operand.precision());
            } else if (isFractionOfPi(operand,4 )) { // tan(PI/4) = 1
                return BigDecimalMath.scalePrec(new BigDecimal("1"), operand.precision());
            } else if (isFractionOfPi(operand, 2)) { // tan(PI/2) = ERROR
                throw new ArithmeticException("Invalid input.");
            } else if (isFractionOfPi(operand, 1)) { // tan(PI) = 0
                return BigDecimalMath.scalePrec(new BigDecimal("0"), operand.precision());
            } else if (isThreeHalvesPi(operand)) {
                throw new ArithmeticException("Invalid input.");
            }
            return BigDecimalMath.tan(operand);
        }, "tan("));
        // Natural log
        unaryOps.put("ln(", new UnaryOperation((BigDecimal operand) -> {
            return BigDecimalMath.log(operand);
        }, "ln("));
        // Log base 10
        unaryOps.put("log(", new UnaryOperation((BigDecimal operand) -> {
            try { // BigDecimalMath returns incorrect decimal value for whole number results > 4.
                int intValue = operand.intValueExact();
                double doubleResult = Math.log10((double) intValue);
                return new BigDecimal(doubleResult);
            } catch (Exception e) {
                if (operand.compareTo(new BigDecimal("10")) == 0) {
                    return BigDecimalMath.scalePrec(new BigDecimal("1"), operand.precision());
                } else if (operand.compareTo(new BigDecimal("0.1")) == 0) {
                    return BigDecimalMath.scalePrec(new BigDecimal("-1"), operand.precision());
                } else {
                    BigDecimal opLogE = BigDecimalMath.log(operand);
                    BigDecimal tenLogE = BigDecimalMath.log(BigDecimalMath.scalePrec(new BigDecimal("10"), operand.precision()));
                    return BigDecimalMath.divideRound(opLogE, tenLogE);
                }
            }
        }, "log("));
    }

    private static BigDecimal convertToRadians(BigDecimal degrees) {
        BigDecimal twoPi = BigDecimalMath.multiplyRound(BigDecimalMath.pi(new MathContext(degrees.precision())), 2);
        BigDecimal opXtwoPi = BigDecimalMath.multiplyRound(degrees, twoPi);
        return BigDecimalMath.divideRound(opXtwoPi, 360);
    }

    /**
     * A helper function used in sin and cos that returns if the operand modulo 2*PI is equal to the specified fractin of PI.
     * When passed PI/2 or PI/4, BigDecimalMath sin and cos functions iterate endlessly until they throw IllegalArgumentExceptions.
     * This helper function aids preventing these values from being passed to the library.
     * @param operand the operand to test.
     * @return true if (operand % (2*PI)) == (PI/divisorOfPi)
     */
    private static boolean isFractionOfPi(BigDecimal operand, int divisorOfPi) {
        MathContext mc = new MathContext(operand.precision());
        if (operand.compareTo(new BigDecimal("0", mc)) == 0) { // operand is 0
            if (divisorOfPi == 0) {
                return true;
            } else {
                return false;
            }
        }
        BigDecimal opMod2Pi = BigDecimalMath.mod2pi(operand);
        BigDecimal fractionOfPi = BigDecimalMath.pi(mc).divide(new BigDecimal(divisorOfPi), mc); // .divide preserves the max precision of the operands.

        return opMod2Pi.compareTo(fractionOfPi) == 0;
    }

    private static boolean isZeroMod2Pi(BigDecimal operand) {
        MathContext mc = new MathContext(operand.precision());
        if (operand.compareTo(new BigDecimal("0")) == 0) {
            return true;
        }
        BigDecimal twoPi = BigDecimalMath.multiplyRound(BigDecimalMath.pi(mc), 2);
        BigDecimal opMod2Pi = BigDecimalMath.mod2pi(operand);
        return opMod2Pi.compareTo(twoPi) == 0;
    }

    private static boolean isThreeHalvesPi(BigDecimal operand) {
        MathContext mc = new MathContext(operand.precision());

        BigDecimal threeHalvesPi = new BigDecimal("3").divide(new BigDecimal("2"), mc).multiply(BigDecimalMath.pi(mc)).round(mc);
        if (BigDecimalMath.mod2pi(operand).compareTo(threeHalvesPi) == 0) {
            return true;
        } else {
            return false;
        }
    }

    private static int min(int left, int right) {
        return left < right ? left : right;
    }


    public static BinaryOperation getBinaryOperation(String tag) {
        return binaryOps.get(tag);
    }

    public static UnaryOperation getUnaryOperation(String tag) {
        return unaryOps.get(tag);
    }
}
