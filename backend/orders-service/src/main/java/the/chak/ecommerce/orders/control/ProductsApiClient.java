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


    @Timeout(2000)
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
