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
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(value = RedisTestResource.class, restrictToAnnotatedClass = true)
class PriceCacheServiceTest {

    @Inject
    PriceCacheService priceCacheService;

    @InjectMock
    ProductsApiClient productsApiClient;

    @Test
    void getProduct_cacheMiss_fetchesFromApiAndCachesResult() {
        // given
        ProductDto expected = new ProductDto();
        expected.setTitle("Widget");
        expected.setPrice(29.99);
        when(productsApiClient.getProduct("prod-cache-1")).thenReturn(expected);

        // when
        ProductDto result = priceCacheService.getProduct("prod-cache-1");

        // then
        assertNotNull(result);
        assertEquals(29.99, result.getPrice());

        ProductDto cached = priceCacheService.getProduct("prod-cache-1");
        assertNotNull(cached);
        assertEquals(29.99, cached.getPrice());
        verify(productsApiClient, times(1)).getProduct("prod-cache-1"); // second call must hit cache
    }

    @Test
    void getProduct_productNotFound_callsApiAndReturnsNull() {
        // given
        when(productsApiClient.getProduct("missing-id")).thenReturn(null);

        // when
        ProductDto result = priceCacheService.getProduct("missing-id");

        // then
        assertNull(result);
        verify(productsApiClient, times(1)).getProduct("missing-id");
    }

    @Test
    void getPrice_cacheMiss_fetchesPriceAndCachesResult() {
        // given
        ProductDto product = new ProductDto();
        product.setTitle("Widget");
        product.setPrice(19.99);
        when(productsApiClient.getProduct("price-prod-1")).thenReturn(product);

        // when
        Double result = priceCacheService.getPrice("price-prod-1");

        // then
        assertNotNull(result);
        assertEquals(19.99, result);

        Double cached = priceCacheService.getPrice("price-prod-1");
        assertNotNull(cached);
        assertEquals(19.99, cached);
        verify(productsApiClient, times(1)).getProduct("price-prod-1"); // second call must hit cache
    }

    @Test
    void getPrice_productNotFound_returnsNull() {
        // given
        when(productsApiClient.getProduct("price-missing")).thenReturn(null);

        // when
        Double result = priceCacheService.getPrice("price-missing");

        // then
        assertNull(result);
        verify(productsApiClient, times(1)).getProduct("price-missing");
    }
}
