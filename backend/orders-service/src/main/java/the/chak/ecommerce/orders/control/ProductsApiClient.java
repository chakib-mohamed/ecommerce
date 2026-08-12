package the.chak.ecommerce.orders.control;


import the.chak.ecommerce.products.boundary.ProductsApi;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProductsApiClient {

    private static final Logger LOG = Logger.getLogger(ProductsApiClient.class);

    @Inject
    @RestClient
    ProductsApi productsApiClient;


    /**
     * Five seconds, not two.
     *
     * <p>Two was the value this was written with rather than one anybody chose, and it is tighter
     * than a first cross-service call can honour on a freshly started stack - class loading, REST
     * client initialisation, connection pool, and a database query at the far end. Confirming an
     * order calls this once per line before it commits anything, so an expiry here surfaces to the
     * buyer as a 500 from confirm, with the order left exactly as it was.
     *
     * <p>Found by stack trace rather than inference: a SmallRye {@code TimeoutException} in the
     * logs of an end-to-end run whose confirm returned 500. The identical default sat on
     * products-service's purchase check and had the identical effect there.
     */
    @Timeout(5000)
    @CircuitBreaker
    public ProductDto getProduct(String productID) {
        long start = System.currentTimeMillis();
        Response response = productsApiClient.getProduct(productID);
        long elapsed = System.currentTimeMillis() - start;
        if (response.getStatus() == 200) {
            LOG.infof("GET products-service productId=%s status=%d elapsed=%dms",
                    productID, response.getStatus(), elapsed);
            return response.readEntity(ProductDto.class);
        }
        LOG.warnf("Product not found in products-service productId=%s elapsed=%dms",
                productID, elapsed);
        return null;
    }
}
