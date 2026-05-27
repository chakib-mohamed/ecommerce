package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.MongoDbTestResource;
import the.chak.ecommerce.products.entity.EmbeddedCategory;
import the.chak.ecommerce.products.entity.EmbeddedPromotion;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class FeaturedProductsResourceTest {

    @BeforeAll
    static void setupLogging() {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @BeforeEach
    void cleanup() {
        ProductMongoEntity.deleteAll();
    }

    @Test
    void getFeaturedProducts_defaultPagination_returnsFirstPage() {
        // given
        for (int i = 0; i < 15; i++) {
            ProductMongoEntity product = new ProductMongoEntity();
            product.setProductID(UUID.randomUUID());
            product.setTitle("Featured Product " + i);
            product.setDescription("Desc " + i);
            product.setImage("img-" + i);
            product.setPrice(10.0 + i);
            product.setCategories(List.of());
            product.setPromotions(List.of());
            product.persist();
        }

        // when
        var response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/products/featured");

        // then
        response.then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(10));
    }

    @Test
    void getFeaturedProducts_customPageSize_returnsPaginatedResults() {
        // given
        for (int i = 0; i < 25; i++) {
            ProductMongoEntity product = new ProductMongoEntity();
            product.setProductID(UUID.randomUUID());
            product.setTitle("Product " + i);
            product.setDescription("Desc " + i);
            product.setImage("img-" + i);
            product.setPrice(20.0 + i);
            product.setCategories(List.of());
            product.setPromotions(List.of());
            product.persist();
        }

        // when
        var response = given()
                .contentType(ContentType.JSON)
                .queryParam("pageSize", 5)
                .when()
                .get("/products/featured");

        // then
        response.then()
                .statusCode(200)
                .body("size()", equalTo(5));
    }

    @Test
    void getFeaturedProducts_secondPage_returnsCorrectOffset() {
        // given
        for (int i = 0; i < 25; i++) {
            ProductMongoEntity product = new ProductMongoEntity();
            product.setProductID(UUID.randomUUID());
            product.setTitle("Item " + i);
            product.setDescription("Desc " + i);
            product.setImage("img-" + i);
            product.setPrice(30.0 + i);
            product.setCategories(List.of());
            product.setPromotions(List.of());
            product.persist();
        }

        // when — get second page with size 10
        var response = given()
                .contentType(ContentType.JSON)
                .queryParam("pageIndex", 1)
                .queryParam("pageSize", 10)
                .when()
                .get("/products/featured");

        // then
        response.then()
                .statusCode(200)
                .body("size()", equalTo(10));
    }

    @Test
    void getFeaturedProducts_withCategoriesAndPromotions_returnsCompleteData() {
        // given
        UUID productId = UUID.randomUUID();
        ProductMongoEntity product = new ProductMongoEntity();
        product.setProductID(productId);
        product.setTitle("Complete Product");
        product.setDescription("Full data test");
        product.setImage("complete-img");
        product.setPrice(99.99);

        EmbeddedCategory cat = new EmbeddedCategory();
        cat.setId(1L);
        cat.setLabel("Electronics");
        product.setCategories(List.of(cat));

        EmbeddedPromotion promo = new EmbeddedPromotion();
        promo.setLabel("Summer Sale");
        promo.setPercentageOff(15.0);
        promo.setActiveFrom(LocalDate.of(2025, 6, 1));
        promo.setActiveTo(LocalDate.of(2025, 8, 31));
        product.setPromotions(List.of(promo));

        product.persist();

        // when
        var response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/products/featured");

        // then
        response.then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].title", equalTo("Complete Product"))
                .body("[0].description", equalTo("Full data test"))
                .body("[0].price", equalTo(99.99f))
                .body("[0].categories", hasSize(1))
                .body("[0].promotions", hasSize(1));
    }

    @Test
    void getFeaturedProducts_emptyDatabase_returnsEmptyList() {
        // given — no products in database

        // when
        var response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/products/featured");

        // then
        response.then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }
}
