package chakmed.ecommerce.orders.control;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.products.boundary.dto.ProductDto;

@ApplicationScoped
public class PriceCacheService {

    @Inject
    RedisDataSource redis;

    @Inject
    ProductsApiClient productsApiClient;

    @ConfigProperty(name = "cart.price-cache.ttl-minutes", defaultValue = "15")
    int ttlMinutes;

    private ValueCommands<String, Double> values;

    @PostConstruct
    void init() {
        values = redis.value(Double.class);
    }

    public Double getPrice(String productId) {
        String key = "price:" + productId;

        Double cached = values.get(key);
        if (cached != null) {
            return cached;
        }

        ProductDto product = productsApiClient.getProduct(productId);
        if (product == null || product.getPrice() == null) {
            return null;
        }

        values.setex(key, (long) ttlMinutes * 60, product.getPrice());
        return product.getPrice();
    }
}
