package the.chak.ecommerce.analytics.repository;

/**
 * Revenue rolled up for one {@code YYYY-MM} bucket. Only months that actually have sales are
 * returned; filling the gaps is the caller's job.
 */
public record MonthlyRevenue(String month, double revenue) {
}
