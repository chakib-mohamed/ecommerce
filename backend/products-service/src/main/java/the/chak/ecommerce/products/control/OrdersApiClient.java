package the.chak.ecommerce.products.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.OrdersApi;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;

/** Purchase verification for reviews - has this reviewer bought this product? */
@ApplicationScoped
public class OrdersApiClient {

    private static final Logger LOG = Logger.getLogger(OrdersApiClient.class);

    @Inject
    @RestClient
    OrdersApi ordersApi;

    @Timeout(2000)
    @CircuitBreaker
    public boolean hasPurchased(String reviewer, String productId) {
        SearchOrdersCommand command = new SearchOrdersCommand();
        command.setUserID(reviewer);
        command.setProductID(productId);
        command.setOffset(0);
        command.setLimit(1);

        long start = System.currentTimeMillis();
        var result = ordersApi.searchOrders(command);
        LOG.infof("POST orders-service /orders/search reviewer=%s productId=%s elapsed=%dms",
                reviewer, productId, System.currentTimeMillis() - start);
        return result.getX() != null && result.getX() > 0;
    }
}
