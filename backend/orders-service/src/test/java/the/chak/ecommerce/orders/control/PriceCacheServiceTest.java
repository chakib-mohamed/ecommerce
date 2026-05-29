package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@ExtendWith(MockitoExtension.class)
class PriceCacheServiceTest {

    @InjectMocks
    PriceCacheService priceCacheService;

    @Mock
    RedisDataSource redis;

    @Mock
    ProductsApiClient productsApiClient;

    @Mock
    ValueCommands<String, Double> priceValues;

    @Mock
    ValueCommands<String, ProductDto> productValues;

    @BeforeEach
    void setUp() {
        when(redis.value(Double.class)).thenReturn(priceValues);
        when(redis.value(ProductDto.class)).thenReturn(productValues);
        priceCacheService.init();
        priceCacheService.ttlMinutes = 15;
    }

    @Test
    void getProduct_cacheMiss_fetchesFromApiAndCachesResult() {
        // given
        String productId = "prod-1";
        ProductDto expected = new ProductDto();
        expected.setPrice(29.99);

        when(productValues.get("product:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(expected);

        // when
        ProductDto result = priceCacheService.getProduct(productId);

        // then
        assertNotNull(result);
        assertEquals(29.99, result.getPrice());
        verify(productValues).setex(eq("product:" + productId), anyLong(), eq(expected));
    }

    @Test
    void getProduct_cacheHit_returnsCachedValue() {
        // given
        String productId = "prod-1";
        ProductDto cached = new ProductDto();
        cached.setPrice(29.99);

        when(productValues.get("product:" + productId)).thenReturn(cached);

        // when
        ProductDto result = priceCacheService.getProduct(productId);

        // then
        assertNotNull(result);
        assertEquals(29.99, result.getPrice());
        verify(productsApiClient, never()).getProduct(anyString());
    }

    @Test
    void getProduct_productNotFound_returnsNull() {
        // given
        String productId = "missing-id";
        when(productValues.get("product:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(null);

        // when
        ProductDto result = priceCacheService.getProduct(productId);

        // then
        assertNull(result);
    }

    @Test
    void getPrice_cacheMiss_fetchesPriceAndCachesResult() {
        // given
        String productId = "prod-1";
        ProductDto product = new ProductDto();
        product.setPrice(19.99);

        when(priceValues.get("price:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(product);

        // when
        Double result = priceCacheService.getPrice(productId);

        // then
        assertEquals(19.99, result);
        verify(priceValues).setex(eq("price:" + productId), anyLong(), eq(19.99));
    }

    @Test
    void getPrice_cacheHit_returnsCachedValue() {
        // given
        String productId = "prod-1";
        when(priceValues.get("price:" + productId)).thenReturn(19.99);

        // when
        Double result = priceCacheService.getPrice(productId);

        // then
        assertEquals(19.99, result);
        verify(productsApiClient, never()).getProduct(anyString());
    }

    @Test
    void getPrice_productNotFound_returnsNull() {
        // given
        String productId = "missing-id";
        when(priceValues.get("price:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(null);

        // when
        Double result = priceCacheService.getPrice(productId);

        // then
        assertNull(result);
    }
}
