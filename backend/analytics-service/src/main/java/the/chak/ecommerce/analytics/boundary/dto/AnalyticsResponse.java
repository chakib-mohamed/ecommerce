package the.chak.ecommerce.analytics.boundary.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Everything the back-office dashboard renders, in one response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    /** Revenue for each of the last 12 months, oldest first. Always 12 entries. */
    private List<MonthSale> sales;

    /** Units and revenue per product, best sellers first. */
    private List<ProductSale> productSales;

    /** Revenue per category, highest first. */
    private List<CategoryRevenue> categoryBreakdown;

    private BigDecimal totalRevenue;
}
