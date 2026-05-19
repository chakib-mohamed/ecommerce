package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(value = RedisTestResource.class, restrictToAnnotatedClass = true)
public class PriceCacheServiceTest {

    @Inject
    PriceCacheService priceCacheService;

    @InjectMock
    ProductsApiClient productsApiClient;

    @Test
    public void getProduct_cacheMiss_fetchesFromApiAndCachesResult() {
        ProductDto expected = new ProductDto();
        expected.setTitle("Widget");
        expected.setPrice(29.99);
        when(productsApiClient.getProduct("prod-cache-1")).thenReturn(expected);

        ProductDto result = priceCacheService.getProduct("prod-cache-1");

        assertNotNull(result);
        assertEquals(29.99, result.getPrice());

        // Second call must hit cache — API called exactly once
        ProductDto cached = priceCacheService.getProduct("prod-cache-1");
        assertNotNull(cached);
        assertEquals(29.99, cached.getPrice());
        verify(productsApiClient, times(1)).getProduct("prod-cache-1");
    }

    @Test
    public void getProduct_productNotFound_callsApiAndReturnsNull() {
        when(productsApiClient.getProduct("missing-id")).thenReturn(null);

        ProductDto result = priceCacheService.getProduct("missing-id");

        assertNull(result);
        // stub bypasses the API entirely — verify the call was made
        verify(productsApiClient, times(1)).getProduct("missing-id");
    }
}
