package the.chak.ecommerce.orders.boundary.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How amounts are represented and rounded on the order and pricing path.
 *
 * <p>Amounts are {@link BigDecimal}, never {@code double}. Binary floating point cannot represent
 * most decimal fractions, so summing line totals as doubles drifts, and the drift lands in what a
 * buyer is charged.
 *
 * <p>Rounding happens <b>once</b>, where a total is finalised, and nowhere else. Rounding
 * intermediate values and then rounding again compounds the error rather than removing it - which
 * is what the platform used to do, applying {@code String.format("%.2f")} in two separate layers.
 *
 * <p>There is one currency and no conversion. The field exists so amounts are not silently
 * currency-less; see section 7 of {@code docs/specs/order-lifecycle.md}.
 */
public final class Money {

    /** The only currency in use. Every amount crossing a boundary is denominated in it. */
    public static final String DEFAULT_CURRENCY = "EUR";

    /** Minor units. Every persisted and published amount carries exactly this scale. */
    public static final int SCALE = 2;

    /** Half-up matches what a person expects when a half-cent is split. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    /**
     * Rounds a finalised amount to the currency's scale. Null passes through so an absent amount
     * stays absent rather than becoming zero.
     */
    public static BigDecimal round(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }

    /**
     * Compares two amounts by value, ignoring scale, so {@code 10.5} and {@code 10.50} are equal.
     * {@link BigDecimal#equals} compares scale too and would call those different.
     */
    public static boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
