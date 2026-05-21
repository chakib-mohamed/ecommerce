package the.chak.ecommerce.products.boundary;

import io.quarkus.jsonb.JsonbConfigCustomizer;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.jboss.logging.Logger;

@Singleton
public class CustomJsonbConfigCustomizer implements JsonbConfigCustomizer {

    private static final Logger LOG = Logger.getLogger(CustomJsonbConfigCustomizer.class);

    @Override
    public void customize(JsonbConfig config) {
        LOG.debug("CustomJsonbConfigCustomizer.customize called");
        config.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES);
    }
}
