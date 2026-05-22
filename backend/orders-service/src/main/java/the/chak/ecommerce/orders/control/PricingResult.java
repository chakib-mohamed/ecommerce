package the.chak.ecommerce.orders.control;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PricingResult {

    private String id;
    private PricingResultOrder order;

    @Getter
    @Setter
    public static class PricingResultOrder {
        private Double price;
    }
}
