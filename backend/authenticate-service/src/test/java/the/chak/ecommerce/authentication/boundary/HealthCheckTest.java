package the.chak.ecommerce.authentication.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import the.chak.ecommerce.authentication.MongoDbTestResource;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
class HealthCheckTest {

    @BeforeAll
    static void setupLogging() {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @Test
    @DisplayName("Reports overall status UP on the aggregate health endpoint")
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
    @DisplayName("Reports status UP on the liveness probe")
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
    @DisplayName("Reports status UP on the readiness probe")
    void readiness_returns200() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }

    @Test
    @DisplayName("Includes an UP mongodb-connectivity check in the readiness probe")
    void readiness_includes_mongodb_probe() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.find{it.name=='mongodb-connectivity'}.status", is("UP"));
    }
}
