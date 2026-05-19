package the.chak.ecommerce.orders.control;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CartIndexInitializer {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "orders")
    String database;

    void onStart(@Observes StartupEvent event) {
        var carts = mongoClient.getDatabase(database).getCollection("carts");

        carts.createIndex(
                Indexes.ascending("userId"),
                new IndexOptions().unique(true)
        );

        carts.createIndex(
                Indexes.ascending("updatedAt"),
                new IndexOptions().expireAfter(30L, TimeUnit.DAYS)
        );
    }
}
