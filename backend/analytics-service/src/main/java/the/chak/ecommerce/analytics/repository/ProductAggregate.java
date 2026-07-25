package the.chak.ecommerce.analytics.repository;

/** Units sold and revenue for a single product, across all completed orders. */
public record ProductAggregate(String productId, String title, long units, double revenue) {
}
