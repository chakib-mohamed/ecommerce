package the.chak.ecommerce.orders.control;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PricingOrder {

    private List<PricingOrderProduct> products;

    @Getter
    @Setter
    public static class PricingOrderProduct {
        private String productID;
        private Integer qty;
        private Double price;
        private Double percentageOff;
    }
}
