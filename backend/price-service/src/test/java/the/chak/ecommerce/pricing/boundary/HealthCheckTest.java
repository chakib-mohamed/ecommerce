package the.chak.ecommerce.pricing.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class HealthCheckTest {

    @BeforeAll
    static void setupLogging() {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @Test
    void health_returns200() {
        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }

    @Test
    void liveness_returns200() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }

    @Test
    void readiness_returns200() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }
}
