package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StorageTestResource.class)
class PromotionsResourceTest {

    private String createdPromotionId;

    @AfterEach
    void cleanup() {
        if (createdPromotionId != null) {
            given().when().delete("/promotions/{id}", createdPromotionId);
            createdPromotionId = null;
        }
    }

    @Test
    void createPromotion_blankLabel_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"product_id\":\"prod-1\",\"label\":\"\",\"percentage_off\":10.0,\"active_from\":\"2025-01-01\",\"active_to\":\"2025-12-31\"}")
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
                .body("{\"product_id\":\"prod-1\",\"label\":\"Valid Label\",\"percentage_off\":150.0,\"active_from\":\"2025-01-01\",\"active_to\":\"2025-12-31\"}")
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
                .body("{\"product_id\":\"prod-1\",\"label\":\"Valid Label\",\"percentage_off\":10.0,\"active_to\":\"2025-12-31\"}")
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
                .body("{\"product_id\":\"\",\"label\":\"Valid Label\",\"percentage_off\":10.0,\"active_from\":\"2025-01-01\",\"active_to\":\"2025-12-31\"}")
                .when().post("/promotions");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    // -- Success paths --

    @Test
    void createPromotion_validRequest_returns201() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{\"product_id\":\"prod-123\",\"label\":\"Summer Sale\",\"percentage_off\":20.0,\"active_from\":\"2025-06-01\",\"active_to\":\"2025-08-31\"}")
                .when().post("/promotions");

        // then
        createdPromotionId = response.then().statusCode(201)
                .body("id", notNullValue())
                .body("label", is("Summer Sale"))
                .body("percentage_off", is(20.0f))
                .body("active_from", is("2025-06-01"))
                .body("active_to", is("2025-08-31"))
                .extract().path("id").toString();
    }

    @Test
    void getPromotions_withExistingPromotion_returnsList() {
        // given — create a promotion first
        createdPromotionId = given().contentType(ContentType.JSON)
                .body("{\"product_id\":\"prod-456\",\"label\":\"Winter Sale\",\"percentage_off\":15.0,\"active_from\":\"2025-12-01\",\"active_to\":\"2025-12-31\"}")
                .when().post("/promotions").then().statusCode(201)
                .extract().path("id").toString();

        // when
        var response = given()
                .when().get("/promotions");

        // then
        response.then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("find { it.label == 'Winter Sale' }.label", is("Winter Sale"))
                .body("find { it.label == 'Winter Sale' }.percentage_off", is(15.0f));
    }

    @Test
    void deletePromotion_existingPromotion_returns200() {
        // given — create a promotion
        String promotionId = given().contentType(ContentType.JSON)
                .body("{\"product_id\":\"prod-789\",\"label\":\"Spring Sale\",\"percentage_off\":25.0,\"active_from\":\"2025-03-01\",\"active_to\":\"2025-05-31\"}")
                .when().post("/promotions").then().statusCode(201)
                .extract().path("id").toString();

        // when
        var response = given()
                .when().delete("/promotions/{id}", promotionId);

        // then
        response.then().statusCode(200);
    }
}
