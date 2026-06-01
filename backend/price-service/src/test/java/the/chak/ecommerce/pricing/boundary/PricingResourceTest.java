package the.chak.ecommerce.pricing.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.List;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;
import io.restassured.RestAssured;
import io.restassured.config.JsonConfig;
import io.restassured.path.json.config.JsonPathConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.pricing.KafkaTestResource;
import the.chak.ecommerce.pricing.MongoDbTestResource;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.control.KafkaPriceEventPublisher;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PricingResourceTest {

    private static final Jsonb JSONB = JsonbBuilder.create(
            new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));

    @InjectMock
    KafkaPriceEventPublisher publisher;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.config = RestAssured.config()
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE));
    }

    private String toJson(Object obj) {
        return JSONB.toJson(obj);
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 200 applying the bulk discount when the quantity exceeds five")
    void calculatePrice_qtyAbove5_appliesDrlDiscount() {
        // given - qty=6 > 5 triggers DRL 5% reduction: 6 * 10.0 * 0.95 = 57.0
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

        // when
        var response = given().contentType(ContentType.JSON).body(toJson(request))
                .when().post("/pricing/calculate");

        // then
        response.then().statusCode(200)
                .body("id", notNullValue())
                .body("order.price", closeTo(57.0, 0.01));
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 200 applying the product's percentage discount to the total")
    void calculatePrice_withPromotion_appliesPercentageOff() {
        // given - qty=1, price=100.0, percentageOff=10: 1 * 100.0 * 0.90 = 90.0
        ProductVO product = new ProductVO();
        product.setProductID("prod-2");
        product.setTitle("Gadget");
        product.setQty(1);
        product.setPrice(100.0);
        product.setPercentageOff(10.0);

        OrderDTO order = new OrderDTO();
        order.setProducts(List.of(product));

        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);

        // when
        var response = given().contentType(ContentType.JSON).body(toJson(request))
                .when().post("/pricing/calculate");

        // then
        response.then().statusCode(200).body("order.price", closeTo(90.0, 0.01));
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 400 with INVALID_ORDER when the order has no products")
    void calculatePrice_emptyProducts_returns400() {
        // given
        OrderDTO order = new OrderDTO();
        order.setProducts(List.of());

        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(order);

        // when
        var response = given().contentType(ContentType.JSON).body(toJson(request))
                .when().post("/pricing/calculate");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("INVALID_ORDER"));
    }

    @Test
    @TestSecurity(user = "test-user")
    @DisplayName("Returns 400 with INVALID_ORDER when the request has no order")
    void calculatePrice_nullOrder_returns400() {
        // given
        PriceCalculationRequest request = new PriceCalculationRequest();
        request.setOrder(null);

        // when
        var response = given().contentType(ContentType.JSON).body(toJson(request))
                .when().post("/pricing/calculate");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("INVALID_ORDER"));
    }
}
