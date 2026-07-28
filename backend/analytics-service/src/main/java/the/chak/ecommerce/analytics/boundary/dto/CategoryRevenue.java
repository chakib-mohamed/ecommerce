package the.chak.ecommerce.analytics.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Revenue for one category, and its share of the total. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRevenue {

    /** Category identifier; {@code uncategorized} when the category is unknown. */
    private String id;

    private String name;

    private Double value;

    /** Share of total revenue, between 0 and 100. */
    private Double pct;
}
