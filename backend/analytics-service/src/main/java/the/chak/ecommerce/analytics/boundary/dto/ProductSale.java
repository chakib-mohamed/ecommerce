package the.chak.ecommerce.analytics.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Units sold and revenue for one product. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSale {

    private String productId;

    private String name;

    private Long units;

    private Double revenue;
}
