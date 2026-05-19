package the.chak.ecommerce.pricing.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.quarkus.test.InjectMock;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.boundary.dto.UpdatePriceRequest;
import the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
public class PriceResourceTest {

    @InjectMock
    KafkaPriceEventPublisher publisher;

    @Test
    public void updatePrice_validPrice_storesAndReturns200() {
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(49.99);

        given().contentType(ContentType.JSON)
                .body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString())
                .then().statusCode(200)
                .body("productId", notNullValue())
                .body("price", is(49.99f));
    }

    @Test
    public void updatePrice_negativePrice_returns400() {
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(-1.0);

        given().contentType(ContentType.JSON)
                .body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString())
                .then().statusCode(400);
    }

    @Test
    public void updatePrice_nullPrice_returns400() {
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(null);

        given().contentType(ContentType.JSON)
                .body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString())
                .then().statusCode(400);
    }
}
