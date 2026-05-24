package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import the.chak.ecommerce.products.KafkaTestResource;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class PromotionsResourceTest {

    @Test
    void createPromotion_blankLabel_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"productID\":\"prod-1\",\"label\":\"\",\"percentageOff\":10.0,\"activeFrom\":\"2025-01-01\",\"activeTo\":\"2025-12-31\"}")
                .when().post("/promotions");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void createPromotion_percentageOffAbove100_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"productID\":\"prod-1\",\"label\":\"Valid Label\",\"percentageOff\":150.0,\"activeFrom\":\"2025-01-01\",\"activeTo\":\"2025-12-31\"}")
                .when().post("/promotions");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void createPromotion_nullActiveFrom_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"productID\":\"prod-1\",\"label\":\"Valid Label\",\"percentageOff\":10.0,\"activeTo\":\"2025-12-31\"}")
                .when().post("/promotions");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void createPromotion_missingProductId_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"productID\":\"\",\"label\":\"Valid Label\",\"percentageOff\":10.0,\"activeFrom\":\"2025-01-01\",\"activeTo\":\"2025-12-31\"}")
                .when().post("/promotions");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }
}
