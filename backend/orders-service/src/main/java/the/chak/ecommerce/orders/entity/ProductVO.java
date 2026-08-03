package the.chak.ecommerce.orders.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVO {
    private String productID;
    private String title;
    private Integer qty;
    private BigDecimal price;
    private Double percentageOff;
}
