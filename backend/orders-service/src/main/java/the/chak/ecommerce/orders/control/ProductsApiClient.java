package the.chak.ecommerce.orders.control;


import the.chak.ecommerce.products.boundary.ProductsApi;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProductsApiClient {

    @Inject
    @RestClient
    ProductsApi productsApiClient;


    @Timeout(2000)
    @CircuitBreaker
    public ProductDto getProduct(String productID) {
        Response response = productsApiClient.getProduct(productID);
        if (response.getStatus() == 200) {
            return response.readEntity(ProductDto.class);
        }
        return null;
    }
}
