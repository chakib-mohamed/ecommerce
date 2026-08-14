package the.chak.ecommerce.analytics.control;

import the.chak.ecommerce.orders.boundary.dto.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.analytics.boundary.dto.AnalyticsResponse;
import the.chak.ecommerce.analytics.boundary.dto.CategoryRevenue;
import the.chak.ecommerce.analytics.boundary.dto.MonthSale;
import the.chak.ecommerce.analytics.boundary.dto.ProductSale;
import the.chak.ecommerce.analytics.repository.CategoryAggregate;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.analytics.repository.MonthlyRevenue;
import the.chak.ecommerce.analytics.repository.ProductAggregate;

/** Assembles the dashboard response from the warehouse rollups. */
@ApplicationScoped
public class AnalyticsService {

    /** How many months the dashboard's trend line covers, including the current one. */
    static final int WINDOW_MONTHS = 12;

    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String UNCATEGORIZED_ID = "uncategorized";
    private static final String UNCATEGORIZED_NAME = "Uncategorized";

    @Inject
    FactSalesLineRepository factRepository;

    /**
     * Builds the dashboard overview: a full 12-month revenue series, per-product sales, the
     * category split, and the total.
     */
    public AnalyticsResponse buildAnalytics() {
        // Cent scale on every published amount, so an empty warehouse reports 0.00 rather than
        // a bare 0 and the response shape does not depend on whether there were any sales.
        BigDecimal totalRevenue = Money.round(factRepository.totalRevenue());
        return new AnalyticsResponse(
                monthlySeries(),
                productSales(),
                categoryBreakdown(totalRevenue),
                totalRevenue);
    }

    /**
     * The trend line always spans the full window, so a quiet month shows as a zero rather than
     * shortening the series and shifting the chart.
     */
    private List<MonthSale> monthlySeries() {
        YearMonth start = YearMonth.now().minusMonths(WINDOW_MONTHS - 1L);
        Map<String, BigDecimal> recorded = factRepository.revenueByMonth(start.format(MONTH_BUCKET))
                .stream()
                .collect(Collectors.toMap(MonthlyRevenue::month, MonthlyRevenue::revenue));

        List<MonthSale> series = new ArrayList<>(WINDOW_MONTHS);
        for (int offset = 0; offset < WINDOW_MONTHS; offset++) {
            YearMonth month = start.plusMonths(offset);
            series.add(new MonthSale(
                    month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    Money.round(recorded.getOrDefault(month.format(MONTH_BUCKET), BigDecimal.ZERO))));
        }
        return series;
    }

    private List<ProductSale> productSales() {
        return factRepository.salesByProduct().stream()
                .map(this::toProductSale)
                .toList();
    }

    private ProductSale toProductSale(ProductAggregate aggregate) {
        return new ProductSale(aggregate.productId(), aggregate.title(), aggregate.units(),
                Money.round(aggregate.revenue()));
    }

    private List<CategoryRevenue> categoryBreakdown(BigDecimal totalRevenue) {
        return factRepository.revenueByCategory().stream()
                .map(aggregate -> toCategoryRevenue(aggregate, totalRevenue))
                .toList();
    }

    private CategoryRevenue toCategoryRevenue(CategoryAggregate aggregate, BigDecimal totalRevenue) {
        boolean known = aggregate.categoryId() != null;
        return new CategoryRevenue(
                known ? String.valueOf(aggregate.categoryId()) : UNCATEGORIZED_ID,
                known ? aggregate.categoryLabel() : UNCATEGORIZED_NAME,
                Money.round(aggregate.revenue()),
                share(aggregate.revenue(), totalRevenue));
    }

    /**
     * A category's percentage of total revenue. Guards the empty warehouse, where every share would
     * otherwise be a division by zero.
     *
     * <p>A share is a ratio rather than an amount, so it is computed and returned as a double. The
     * inputs are exact; only the presentation of the ratio is approximate.
     */
    private double share(BigDecimal revenue, BigDecimal totalRevenue) {
        return totalRevenue.signum() == 0 ? 0d
                : revenue.doubleValue() / totalRevenue.doubleValue() * 100d;
    }
}
