package the.chak.ecommerce.pricing.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.quarkus.test.InjectMock;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.boundary.dto.UpdatePriceRequest;
import the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class PriceResourceTest {

    @InjectMock
    KafkaPriceEventPublisher publisher;

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 200 with the stored price when updating with a valid price")
    void updatePrice_validPrice_returns200WithStoredPrice() {
        // given
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(49.99);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString());

        // then
        response.then().statusCode(200)
                .body("product_id", notNullValue())
                .body("price", is(49.99f));
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 400 with VALIDATION_ERROR when updating with a negative price")
    void updatePrice_negativePrice_returns400() {
        // given
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(-1.0);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString());

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 400 with VALIDATION_ERROR when updating with no price")
    void updatePrice_nullPrice_returns400() {
        // given
        UpdatePriceRequest request = new UpdatePriceRequest();
        request.setPrice(null);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().put("/prices/{productId}", UUID.randomUUID().toString());

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }
}
