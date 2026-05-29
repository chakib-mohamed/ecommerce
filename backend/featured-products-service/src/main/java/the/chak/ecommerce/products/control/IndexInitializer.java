package the.chak.ecommerce.products.control;

import com.mongodb.client.model.IndexOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.Document;
import the.chak.ecommerce.products.repository.ProductMongoRepository;

@ApplicationScoped
public class IndexInitializer {

    @Inject
    ProductMongoRepository productMongoRepository;

    void onStart(@Observes StartupEvent event) {
        productMongoRepository.mongoCollection()
                .createIndex(new Document("productID", 1), new IndexOptions().unique(true));
    }
}
