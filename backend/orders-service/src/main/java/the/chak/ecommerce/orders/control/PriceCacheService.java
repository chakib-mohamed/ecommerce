package the.chak.ecommerce.orders.control;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@ApplicationScoped
public class PriceCacheService {

    private static final Logger LOG = Logger.getLogger(PriceCacheService.class);

    @Inject
    RedisDataSource redis;

    @Inject
    ProductsApiClient productsApiClient;

    @ConfigProperty(name = "cart.price-cache.ttl-minutes", defaultValue = "15")
    int ttlMinutes;

    private ValueCommands<String, Double> priceValues;
    private ValueCommands<String, ProductDto> productValues;

    @PostConstruct
    void init() {
        priceValues = redis.value(Double.class);
        productValues = redis.value(ProductDto.class);
    }

    public Double getPrice(String productId) {
        String key = "price:" + productId;

        Double cached = priceValues.get(key);
        if (cached != null) {
            return cached;
        }

        LOG.infof("Price cache miss productId=%s - fetching from products-service", productId);
        ProductDto product = productsApiClient.getProduct(productId);
        if (product == null || product.getPrice() == null) {
            return null;
        }

        priceValues.setex(key, (long) ttlMinutes * 60, product.getPrice());
        return product.getPrice();
    }

    public ProductDto getProduct(String productId) {
        String key = "product:" + productId;
        ProductDto cached = productValues.get(key);
        if (cached != null) {
            return cached;
        }
        ProductDto product = productsApiClient.getProduct(productId);
        if (product == null) {
            return null;
        }
        productValues.setex(key, (long) ttlMinutes * 60, product);
        return product;
    }
}
