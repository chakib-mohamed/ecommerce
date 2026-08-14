package the.chak.ecommerce.analytics.boundary;

import java.math.BigDecimal;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.analytics.control.IngestionService;
import the.chak.ecommerce.analytics.repository.DimProductRepository;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@QuarkusTest
@Tag("integration")
class AnalyticsResourceTest {

    private static final UUID PRODUCT_UUID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Inject
    IngestionService ingestionService;

    @Inject
    FactSalesLineRepository factRepository;

    @Inject
    DimProductRepository dimRepository;

    @BeforeEach
    @Transactional
    void clearWarehouse() {
        factRepository.deleteAll();
        dimRepository.deleteAll();
    }

    private void seedConfirmedSale() {
        CategoryDto lighting = new CategoryDto();
        lighting.setId(7L);
        lighting.setLabel("Lighting");

        // Mirrors a real product event: it carries the categories the product is filed under and
        // leaves categoryId/subcategoryId empty. Setting categoryId here would let ingestion pass
        // by reading a field the wire never actually carries.
        ProductDto product = new ProductDto();
        product.setUuid(PRODUCT_UUID);
        product.setTitle("Desk lamp");
        product.setCategories(List.of(lighting));
        ingestionService.upsertProduct(product);

        ProductVO line = new ProductVO();
        line.setProductID(PRODUCT_UUID.toString());
        line.setTitle("Desk lamp");
        line.setQty(2);
        line.setPrice(BigDecimal.valueOf(100));
        line.setPercentageOff(10d);

        OrderDTO order = new OrderDTO();
        order.setId("order-1");
        order.setCreationDate(LocalDateTime.now());
        order.setUserID("buyer@example.com");
        order.setProducts(List.of(line));
        ingestionService.ingestOrder(order);
    }

    @Test
    @DisplayName("Rejects an unauthenticated request for the dashboard figures")
    void getAnalytics_anonymous_isUnauthorized() {
        given().when().get("/analytics").then().statusCode(401);
    }

    @Test
    @DisplayName("Returns a full twelve-month series when the warehouse is empty")
    @TestSecurity(user = "admin")
    void getAnalytics_emptyWarehouse_returnsTwelveMonthsAndZeroTotal() {
        given()
                .when().get("/analytics")
                .then()
                .statusCode(200)
                .body("sales", hasSize(12))
                .body("total_revenue", equalTo(0.0f));
    }

    @Test
    @DisplayName("Reports a completed sale under its product and category, in snake_case")
    @TestSecurity(user = "admin")
    void getAnalytics_confirmedSale_reportsItAgainstProductAndCategory() {
        // given
        seedConfirmedSale();

        // then
        given()
                .when().get("/analytics")
                .then()
                .statusCode(200)
                .body("sales", hasSize(12))
                .body("total_revenue", equalTo(180.0f))
                .body("product_sales", hasSize(1))
                .body("product_sales[0].product_id", equalTo(PRODUCT_UUID.toString()))
                .body("product_sales[0].name", equalTo("Desk lamp"))
                .body("product_sales[0].units", equalTo(2))
                .body("product_sales[0].revenue", equalTo(180.0f))
                .body("category_breakdown", hasSize(1))
                .body("category_breakdown[0].id", equalTo("7"))
                .body("category_breakdown[0].name", equalTo("Lighting"))
                .body("category_breakdown[0].pct", equalTo(100.0f));
    }

    @Test
    @DisplayName("Counts a redelivered order once, not twice")
    @TestSecurity(user = "admin")
    void getAnalytics_orderDeliveredTwice_countsItOnce() {
        // given
        seedConfirmedSale();
        seedConfirmedSale();

        // then
        given()
                .when().get("/analytics")
                .then()
                .statusCode(200)
                .body("total_revenue", equalTo(180.0f))
                .body("product_sales", hasSize(1))
                .body("product_sales[0].units", equalTo(2));
    }
}
