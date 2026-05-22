package the.chak.ecommerce.orders.control;

import lombok.Data;

@Data
public class PricingResult {

    private String id;
    private PricingResultOrder order;

    @Data
    public static class PricingResultOrder {
        private Double price;
    }
}
