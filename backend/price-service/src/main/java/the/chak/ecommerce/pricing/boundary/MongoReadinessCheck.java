package the.chak.ecommerce.pricing.boundary;

import com.mongodb.client.MongoClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class MongoReadinessCheck implements HealthCheck {

    @Inject
    MongoClient mongoClient;

    @Override
    public HealthCheckResponse call() {
        try {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            return HealthCheckResponse.named("mongodb-connectivity").up().build();
        } catch (Exception e) {
            return HealthCheckResponse.named("mongodb-connectivity").down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
