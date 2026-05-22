package the.chak.ecommerce.orders.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductVO {
    private String productID;
    private String title;
    private Integer qty;
    private Double price;
    private Double percentageOff;
}
