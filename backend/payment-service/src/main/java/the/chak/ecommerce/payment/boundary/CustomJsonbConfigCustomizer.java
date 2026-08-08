package the.chak.ecommerce.payment.boundary;

import io.quarkus.jsonb.JsonbConfigCustomizer;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;

/**
 * The JSON rules every service here shares: snake_case names, and null fields left out entirely.
 *
 * <p>This is not decoration. Without it JSON-B falls back to camelCase, and every message crossing
 * this service's boundary is silently wrong in both directions - {@code payment_method} on an
 * incoming capture binds to nothing and arrives null, and the replies this service publishes go
 * out in a shape orders-service cannot read. Nothing fails loudly: the fields are simply absent.
 */
@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {

    @Override
    public void customize(JsonbConfig config) {
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
        config.withNullValues(false);
    }
}
