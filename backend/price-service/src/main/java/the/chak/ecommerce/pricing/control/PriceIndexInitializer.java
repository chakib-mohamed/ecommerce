package the.chak.ecommerce.pricing.control;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PriceIndexInitializer {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "price-service")
    String database;

    void onStart(@Observes StartupEvent event) {
        mongoClient.getDatabase(database)
                .getCollection("prices")
                .createIndex(
                        Indexes.ascending("productId"),
                        new IndexOptions().unique(true)
                );
    }
}
