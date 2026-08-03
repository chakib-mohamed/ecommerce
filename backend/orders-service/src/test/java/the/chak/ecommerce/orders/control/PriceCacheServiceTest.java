package the.chak.ecommerce.orders.control;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    ValueCommands<String, BigDecimal> priceValues;

    @Mock
    ValueCommands<String, ProductDto> productValues;

    @BeforeEach
    void setUp() {
        when(redis.value(BigDecimal.class)).thenReturn(priceValues);
        when(redis.value(ProductDto.class)).thenReturn(productValues);
        priceCacheService.init();
        priceCacheService.ttlMinutes = 15;
    }

    @Test
    @DisplayName("Fetches the product from the API and caches it on a cache miss")
    void getProduct_cacheMiss_fetchesFromApiAndCachesResult() {
        // given
        String productId = "prod-1";
        ProductDto expected = new ProductDto();
        expected.setPrice(BigDecimal.valueOf(29.99));

        when(productValues.get("product:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(expected);

        // when
        ProductDto result = priceCacheService.getProduct(productId);

        // then
        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(29.99).compareTo(result.getPrice()),
                "expected 29.99, was " + result.getPrice());
        verify(productValues).setex(eq("product:" + productId), anyLong(), eq(expected));
    }

    @Test
    @DisplayName("Returns the cached product without calling the API on a cache hit")
    void getProduct_cacheHit_returnsCachedValue() {
        // given
        String productId = "prod-1";
        ProductDto cached = new ProductDto();
        cached.setPrice(BigDecimal.valueOf(29.99));

        when(productValues.get("product:" + productId)).thenReturn(cached);

        // when
        ProductDto result = priceCacheService.getProduct(productId);

        // then
        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(29.99).compareTo(result.getPrice()),
                "expected 29.99, was " + result.getPrice());
        verify(productsApiClient, never()).getProduct(anyString());
    }

    @Test
    @DisplayName("Returns null when the product is missing from both cache and API")
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
    @DisplayName("Fetches the price from the product API and caches it on a cache miss")
    void getPrice_cacheMiss_fetchesPriceAndCachesResult() {
        // given
        String productId = "prod-1";
        ProductDto product = new ProductDto();
        product.setPrice(BigDecimal.valueOf(19.99));

        when(priceValues.get("price:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(product);

        // when
        BigDecimal result = priceCacheService.getPrice(productId);

        // then
        assertEquals(0, BigDecimal.valueOf(19.99).compareTo(result),
                "expected 19.99, was " + result);
        verify(priceValues).setex(eq("price:" + productId), anyLong(), eq(BigDecimal.valueOf(19.99)));
    }

    @Test
    @DisplayName("Returns the cached price without calling the API on a cache hit")
    void getPrice_cacheHit_returnsCachedValue() {
        // given
        String productId = "prod-1";
        when(priceValues.get("price:" + productId)).thenReturn(BigDecimal.valueOf(19.99));

        // when
        BigDecimal result = priceCacheService.getPrice(productId);

        // then
        assertEquals(0, BigDecimal.valueOf(19.99).compareTo(result),
                "expected 19.99, was " + result);
        verify(productsApiClient, never()).getProduct(anyString());
    }

    @Test
    @DisplayName("Returns null when the price cannot be resolved from cache or API")
    void getPrice_productNotFound_returnsNull() {
        // given
        String productId = "missing-id";
        when(priceValues.get("price:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(null);

        // when
        BigDecimal result = priceCacheService.getPrice(productId);

        // then
        assertNull(result);
    }

    @Test
    @DisplayName("Returns null without caching when the product exists but carries no price")
    void getPrice_productWithoutPrice_returnsNullAndCachesNothing() {
        // given - a product the catalog knows about but has not priced
        String productId = "unpriced-id";
        ProductDto unpriced = new ProductDto();
        unpriced.setTitle("Not priced yet");
        when(priceValues.get("price:" + productId)).thenReturn(null);
        when(productsApiClient.getProduct(productId)).thenReturn(unpriced);

        // when
        BigDecimal result = priceCacheService.getPrice(productId);

        // then - caching a null price would serve it for the whole TTL
        assertNull(result);
        verify(priceValues, never()).setex(anyString(), anyLong(), any());
    }
}
