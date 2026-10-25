package app.vibefinancier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class Money {
    private Money() {}
    public static BigDecimal decimal(String value) {
        if (value == null || !value.matches("-?\\d{1,38}(\\.\\d{1,18})?"))
            throw new IllegalArgumentException("Enter a decimal amount (up to 18 fractional digits).");
        return new BigDecimal(value);
    }
    public static int scale(String code) {
        return switch (code) { case "TON" -> 9; case "BTC" -> 8; case "ETH" -> 18; case "USDT", "USDC" -> 6;
            default -> { try { int digits=Currency.getInstance(code).getDefaultFractionDigits();if(digits<0)throw new IllegalArgumentException();yield digits; }
                catch (Exception e) { throw new IllegalArgumentException("Unknown currency. Set its precision explicitly."); } } };
    }
    public static String input(String value, String currency) {
        BigDecimal d = decimal(value.trim().replace(',', '.'));
        if (d.signum() < 0) throw new IllegalArgumentException("Enter a positive amount; select its direction separately.");
        try { return d.setScale(scale(currency), RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Too many decimal places for " + currency); }
    }
    public static String convert(String amount, String rate, int scale) {
        BigDecimal r = decimal(rate);
        if (r.signum() <= 0) throw new IllegalArgumentException("The exchange rate must be positive.");
        return decimal(amount).multiply(r).setScale(scale, RoundingMode.HALF_EVEN).toPlainString();
    }
    public static String plain(BigDecimal d) { return d.stripTrailingZeros().toPlainString(); }
}
