package the.chak.ecommerce.products.control;

import com.mongodb.client.model.IndexOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bson.Document;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@ApplicationScoped
public class IndexInitializer {

    void onStart(@Observes StartupEvent event) {
        ProductMongoEntity.mongoCollection()
                .createIndex(new Document("productID", 1), new IndexOptions().unique(true));
    }
}
