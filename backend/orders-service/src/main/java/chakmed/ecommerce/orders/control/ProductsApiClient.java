package chakmed.ecommerce.orders.control;


import chakmed.ecommerce.products.boundary.ProductsApi;
import chakmed.ecommerce.products.entity.ProductDTO;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ProductsApiClient {

    @Inject
    @RestClient
    ProductsApi productsApiClient;


    @Timeout(2000)
    @CircuitBreaker
    public ProductDTO getProduct(String productID) {
        return productsApiClient.getProduct(productID).readEntity(ProductDTO.class);
    }
}
