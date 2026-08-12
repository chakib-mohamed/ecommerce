package the.chak.ecommerce.orders.control;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Indexes for the {@code order} collection, one per query shape the service actually issues.
 *
 * <p>The carts collection has had indexes since it existed; orders never did, and got away with it
 * only because every query was a collection scan over a small collection. Sorting the history
 * newest-first turned each of those scans into a scan <em>plus an in-memory sort</em>, which is
 * both slower and bounded - Mongo aborts an in-memory sort once it exceeds 32MB, so an unindexed
 * sort does not degrade gracefully, it starts failing.
 *
 * <p>That matters beyond latency because one caller of the search has a hard deadline:
 * products-service checks purchase eligibility through it under a {@code @Timeout}, and a timeout
 * there surfaces to a reviewer as a 500 rather than a verdict.
 */
@ApplicationScoped
public class OrderIndexInitializer {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "orders")
    String database;

    void onStart(@Observes StartupEvent event) {
        var orders = mongoClient.getDatabase(database).getCollection("order");

        // Order history: filter by buyer, newest first. The sort key is part of the index so the
        // sort is served by it rather than done in memory after the fact.
        orders.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("userID"),
                        Indexes.descending("creationDate")),
                new IndexOptions().name("order_user_recent"));

        // Purchase verification: this buyer, this product, in a state that means they paid.
        // Selective enough that the trailing sort is over a handful of documents.
        orders.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("userID"),
                        Indexes.ascending("products.productID"),
                        Indexes.ascending("status")),
                new IndexOptions().name("order_user_product_status"));

        // The saga deadline sweep, which runs on a timer and would otherwise scan the whole
        // collection every tick to find the few orders that are actually overdue.
        orders.createIndex(
                Indexes.ascending("stepDeadline"),
                new IndexOptions().name("order_step_deadline").sparse(true));
    }
}
