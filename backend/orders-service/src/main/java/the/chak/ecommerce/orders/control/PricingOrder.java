package the.chak.ecommerce.orders.control;

import java.util.List;
import lombok.Data;

@Data
public class PricingOrder {

    private List<PricingOrderProduct> products;

    @Data
    public static class PricingOrderProduct {
        private String productID;
        private Integer qty;
        private Double price;
        private Double percentageOff;
    }
}
