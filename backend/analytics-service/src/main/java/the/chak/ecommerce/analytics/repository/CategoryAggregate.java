package the.chak.ecommerce.analytics.repository;

/**
 * Revenue for one category. {@code categoryId} is null for sales whose product has no known
 * category -- the caller groups those under a single "uncategorized" entry.
 */
public record CategoryAggregate(Long categoryId, String categoryLabel, double revenue) {
}
