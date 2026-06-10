package the.chak.ecommerce.pricing.boundary;

import static io.restassured.RestAssured.given;
import java.time.Duration;
import java.util.List;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher;

/**
 * Exercises REAL JWT verification on the @Authenticated pricing endpoint by presenting an
 * RS256-signed bearer token, rather than the @TestSecurity mock identity the other suites use.
 * The token carries only sub/iat/exp - matching the tokens authenticate-service mints.
 */
@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PricingJwtVerificationTest {

    private static final Jsonb JSONB = JsonbBuilder.create(
            new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));

    @InjectMock
    KafkaPriceEventPublisher publisher;

    private String signedToken() {
        return Jwt.claims()
                .subject("alice@example.com")
                .expiresIn(Duration.ofMinutes(5))
                .sign();
    }

    private String validRequestBody() {
        ProductVO product = new ProductVO();
        product.setProductID("prod-1");
        product.setTitle("Widget");
        product.setQty(6);
        product.setPrice(10.0);
        product.setPercentageOff(null);
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(product));
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);
        return JSONB.toJson(request);
    }

    @Test
    @DisplayName("Accepts a request bearing a valid signed JWT and reaches the handler")
    void calculatePrice_validSignedToken_isAuthorized() {
        given().auth().oauth2(signedToken())
                .contentType(ContentType.JSON).body(validRequestBody())
                .when().post("/pricing/calculate")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("Rejects a request with no bearer token as unauthorized")
    void calculatePrice_noToken_returns401() {
        given().contentType(ContentType.JSON).body(validRequestBody())
                .when().post("/pricing/calculate")
                .then().statusCode(401);
    }
}
