package the.chak.ecommerce.orders.control;

import lombok.Data;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;

// Outgoing contract for the orders -> pricing hop. Mirrors price-service's PriceCalculationRequest
// envelope ({ id, order }) so the products land under 'order'; a bare products list leaves the
// pricing side's order null and is rejected. OrderDTO/ProductVO are the shared orders-api types,
// so field names serialize identically on both ends.
@Data
public class PricingRequest {

    private String id;
    private OrderDTO order;
}
