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

    /**
     * Five seconds, not two.
     *
     * <p>Two was the value this was written with rather than one anybody chose, and it is tighter
     * than the call can honour: this is a cross-service HTTP round trip that a freshly started
     * JVM makes for the first time - class loading, REST client initialisation, connection pool,
     * and a database query on the other side. It exceeded two seconds often enough to be caught in
     * CI, and a buyer submitting the first review after a deployment hits exactly the same window.
     *
     * <p>What they see when it expires is the reason this matters: the timeout propagates as a
     * technical error, so the answer to "may I review this?" arrives as a 500. The status is
     * arguably honest - the system genuinely does not know - but it is not a good answer, and
     * whether an unknown verdict should read as a refusal instead is a contract question rather
     * than a tuning one.
     *
     * <p>Still bounded, and deliberately: the point of a deadline here is that a review submission
     * fails quickly when orders-service is unreachable rather than hanging on it.
     */
    @Timeout(5000)
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
