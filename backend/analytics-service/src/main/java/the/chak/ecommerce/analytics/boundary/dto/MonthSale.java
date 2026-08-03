package the.chak.ecommerce.analytics.boundary.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Revenue for one month of the dashboard's trend line. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthSale {

    /** Short month label, e.g. {@code Jan}. */
    private String month;

    private BigDecimal value;
}
