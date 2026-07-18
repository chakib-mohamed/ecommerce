package the.chak.ecommerce.orders.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// No-args constructor is required for JSON-B to deserialize this as a REST client response
// (products-service calls OrdersApi.searchOrders for purchase verification); server-side
// construction still uses the all-args constructor.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tuple<X, Y> {

    private X x;
    private Y y;
}
