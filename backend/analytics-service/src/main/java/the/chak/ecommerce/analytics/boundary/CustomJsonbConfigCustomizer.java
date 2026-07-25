package the.chak.ecommerce.analytics.boundary;

import io.quarkus.jsonb.JsonbConfigCustomizer;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;

/**
 * Applies the platform's JSON contract: snake_case field names and omitted nulls. The ingested
 * event payloads share this configuration, so they are read in the same shape they were written.
 */
@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {

    @Override
    public void customize(JsonbConfig config) {
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
        config.withNullValues(false);
    }
}
