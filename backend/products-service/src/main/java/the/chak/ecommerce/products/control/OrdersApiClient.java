package the.chak.ecommerce.products.control;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.OrdersApi;
import the.chak.ecommerce.orders.boundary.dto.OrderStatus;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;

/** Purchase verification for reviews - has this reviewer bought this product? */
@ApplicationScoped
public class OrdersApiClient {

    private static final Logger LOG = Logger.getLogger(OrdersApiClient.class);

    @Inject
    @RestClient
    OrdersApi ordersApi;

    /**
     * The states in which the buyer has paid for the order and kept it.
     *
     * <p>Placing an order is not buying one. An order can be created and abandoned at no cost, so
     * counting any order at all would let anybody mint a "verified purchase" on any product in the
     * catalogue - and a badge anyone can mint is worth less than no badge. REFUNDED is left out
     * because the buyer has been made whole; see docs/specs/product-reviews.md, which records that
     * as a judgement call rather than an obvious truth.
     */
    private static final List<OrderStatus> PURCHASED =
            List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    @Timeout(2000)
    @CircuitBreaker
    public boolean hasPurchased(String reviewer, String productId) {
        SearchOrdersCommand command = new SearchOrdersCommand();
        command.setUserID(reviewer);
        command.setProductID(productId);
        command.setStatuses(PURCHASED);
        command.setOffset(0);
        command.setLimit(1);

        long start = System.currentTimeMillis();
        var result = ordersApi.searchOrders(command);
        LOG.infof("POST orders-service /orders/search reviewer=%s productId=%s elapsed=%dms",
                reviewer, productId, System.currentTimeMillis() - start);
        return result.getX() != null && result.getX() > 0;
    }
}
