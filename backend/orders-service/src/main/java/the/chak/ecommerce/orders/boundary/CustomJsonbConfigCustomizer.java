package the.chak.ecommerce.orders.boundary;

import io.quarkus.jsonb.JsonbConfigCustomizer;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;

@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {

    @Override
    public void customize(JsonbConfig config) {
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
        config.withNullValues(false);
    }
}
