package the.chak.ecommerce.analytics.repository;

import java.math.BigDecimal;

/** Units sold and revenue for a single product, across all completed orders. */
public record ProductAggregate(String productId, String title, long units, BigDecimal revenue) {
}
