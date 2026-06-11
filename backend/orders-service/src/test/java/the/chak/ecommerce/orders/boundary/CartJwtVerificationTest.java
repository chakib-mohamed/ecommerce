package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.repository.CartRepository;

/**
 * Exercises REAL JWT verification on the @Authenticated cart endpoint by presenting an
 * RS256-signed bearer token, rather than the @TestSecurity mock identity the other suites use.
 * The token carries only sub/iat/exp - matching the tokens authenticate-service mints.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class CartJwtVerificationTest {

    @Inject
    CartRepository cartRepository;

    @BeforeEach
    void cleanup() {
        cartRepository.deleteAll();
    }

    private String signedToken() {
        return Jwt.claims()
                .subject("alice@example.com")
                .expiresIn(Duration.ofMinutes(5))
                .sign();
    }

    @Test
    @DisplayName("Accepts a request bearing a valid signed JWT and reaches the handler")
    void getCart_validSignedToken_isAuthorized() {
        // auth passes, then the empty repository yields 404 - the point is it is NOT 401
        given().auth().oauth2(signedToken())
                .when().get("/cart")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("Rejects a request with no bearer token as unauthorized")
    void getCart_noToken_returns401() {
        given().when().get("/cart")
                .then().statusCode(401);
    }
}
