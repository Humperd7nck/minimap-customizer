package com.minimapfilter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Bounded decimal arithmetic, never a script engine or operating-system command. */
final class InfoPanelCalculator
{
    static final int MAX_INPUT = 256;
    private static final int MAX_DEPTH = 16;
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    static BigDecimal evaluate(String expression)
    {
        if (expression == null || expression.trim().isEmpty())
        {
            throw new IllegalArgumentException("Enter a calculation.");
        }
        if (expression.length() > MAX_INPUT)
        {
            throw new IllegalArgumentException("Use at most 256 characters.");
        }
        Parser parser = new Parser(expression);
        BigDecimal value = parser.expression(0);
        parser.spaces();
        if (parser.pos != parser.text.length()) { throw parser.error("Unexpected character"); }
        return bounded(value);
    }

    static String display(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.signum() == 0) { return "0"; }
        int exponent = normalized.precision() - normalized.scale() - 1;
        return exponent > 20 || exponent < -8 ? normalized.toEngineeringString() : normalized.toPlainString();
    }

    static String compact(BigDecimal value)
    {
        return display(value.round(new MathContext(8, RoundingMode.HALF_UP)));
    }

    private static BigDecimal bounded(BigDecimal value)
    {
        if (value.signum() != 0)
        {
            long exponent = (long) value.precision() - value.scale() - 1;
            if (exponent > 100 || exponent < -100)
            {
                throw new IllegalArgumentException("Result is outside the supported range (1e-100 to 1e100).");
            }
        }
        return value.round(PRECISION);
    }

    private static final class Parser
    {
        final String text;
        int pos;
        Parser(String text)
        {
            this.text = text.replace('\u00d7', '*').replace('\u00f7', '/');
        }
        void spaces() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) { pos++; } }
        boolean take(char c)
        {
            spaces();
            if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; }
            return false;
        }
        IllegalArgumentException error(String message)
        {
            return new IllegalArgumentException(message + " at position " + (pos + 1) + ".");
        }
        BigDecimal expression(int depth)
        {
            BigDecimal value = term(depth);
            for (;;)
            {
                if (take('+')) { value = bounded(value.add(term(depth), PRECISION)); }
                else if (take('-')) { value = bounded(value.subtract(term(depth), PRECISION)); }
                else { return value; }
            }
        }
        BigDecimal term(int depth)
        {
            BigDecimal value = factor(depth);
            for (;;)
            {
                if (take('*')) { value = bounded(value.multiply(factor(depth), PRECISION)); }
                else if (take('/'))
                {
                    BigDecimal divisor = factor(depth);
                    if (divisor.signum() == 0) { throw new IllegalArgumentException("Cannot divide by zero."); }
                    value = bounded(value.divide(divisor, PRECISION));
                }
                else { return value; }
            }
        }
        BigDecimal factor(int depth)
        {
            if (depth > MAX_DEPTH) { throw new IllegalArgumentException("Too many nested operations."); }
            if (take('+')) { return factor(depth + 1); }
            if (take('-')) { return factor(depth + 1).negate(); }
            if (take('('))
            {
                BigDecimal value = expression(depth + 1);
                if (!take(')')) { throw error("Expected )"); }
                return value;
            }
            spaces();
            int start = pos;
            int digits = 0;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) { pos++; digits++; }
            if (pos < text.length() && text.charAt(pos) == '.')
            {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) { pos++; digits++; }
            }
            if (digits == 0) { throw error("Expected a number"); }
            if (digits > 34) { throw new IllegalArgumentException("Use at most 34 digits per number."); }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E'))
            {
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) { pos++; }
                int expStart = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) { pos++; }
                if (pos == expStart || pos - expStart > 3) { throw error("Invalid exponent"); }
            }
            try { return bounded(new BigDecimal(text.substring(start, pos), PRECISION)); }
            catch (NumberFormatException ex) { throw error("Invalid number"); }
        }
    }
    private InfoPanelCalculator() { }
}
