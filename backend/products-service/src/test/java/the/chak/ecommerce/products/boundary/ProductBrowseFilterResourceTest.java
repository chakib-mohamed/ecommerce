package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;

/**
 * Covers the browse contract on {@code GET /products}: the optional {@code category_id} /
 * {@code subcategory_id} filters and their interaction with {@code page}/{@code size}. Each test
 * builds its own fresh category subtree and files uniquely-titled products under it, so the
 * assertions are isolated from seeded and concurrently-created catalog data.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class ProductBrowseFilterResourceTest {

    private final List<String> createdProductUuids = new ArrayList<>();

    static {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @AfterEach
    void cleanup() {
        createdProductUuids.forEach(uuid -> given().when().delete("/products/{id}", uuid));
        createdProductUuids.clear();
    }

    private int createCategory(String label, Integer parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("label", label);
        if (parentId != null) {
            body.put("parent_id", parentId);
        }
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/categories")
                .then().statusCode(201).extract().path("id");
    }

    private void createProduct(String title, Integer categoryId, Integer subcategoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("price", 10.0);
        if (categoryId != null) {
            body.put("category_id", categoryId);
        }
        if (subcategoryId != null) {
            body.put("subcategory_id", subcategoryId);
        }
        String uuid = given().contentType(ContentType.JSON).body(body)
                .when().post("/products")
                .then().statusCode(201).extract().path("uuid");
        createdProductUuids.add(uuid);
    }

    @Test
    @DisplayName("Filtering by category returns products filed directly under it and under its subcategories, excluding others")
    void getProducts_filterByCategory_includesSubcategoryProductsAndExcludesOthers() {
        // given
        String token = UUID.randomUUID().toString();
        int category = createCategory("BrowseCat " + token, null);
        int subcategory = createCategory("BrowseSub " + token, category);
        int otherCategory = createCategory("OtherCat " + token, null);

        String directTitle = "Direct " + token;
        String subTitle = "Sub " + token;
        String otherTitle = "Other " + token;
        createProduct(directTitle, category, null);
        createProduct(subTitle, category, subcategory);
        createProduct(otherTitle, otherCategory, null);

        // when / then
        given().when().get("/products?category_id=" + category + "&size=100")
                .then().statusCode(200)
                .body("title", hasItems(directTitle, subTitle))
                .body("title", not(hasItem(otherTitle)));
    }

    @Test
    @DisplayName("Filtering by subcategory returns only the products filed under that subcategory")
    void getProducts_filterBySubcategory_returnsOnlySubcategoryProducts() {
        // given
        String token = UUID.randomUUID().toString();
        int category = createCategory("BrowseCat " + token, null);
        int subcategory = createCategory("BrowseSub " + token, category);

        String directTitle = "Direct " + token;
        String subTitle = "Sub " + token;
        createProduct(directTitle, category, null);
        createProduct(subTitle, category, subcategory);

        // when / then
        given().when().get("/products?subcategory_id=" + subcategory + "&size=100")
                .then().statusCode(200)
                .body("title", hasItem(subTitle))
                .body("title", not(hasItem(directTitle)));
    }

    @Test
    @DisplayName("Pages the products within a category and returns an empty page past the last one")
    void getProducts_filterByCategory_paginates() {
        // given
        String token = UUID.randomUUID().toString();
        int category = createCategory("PageCat " + token, null);
        createProduct("P1 " + token, category, null);
        createProduct("P2 " + token, category, null);

        // when / then
        given().when().get("/products?category_id=" + category + "&size=1&page=0")
                .then().statusCode(200).body("size()", is(1));
        given().when().get("/products?category_id=" + category + "&size=1&page=1")
                .then().statusCode(200).body("size()", is(1));
        given().when().get("/products?category_id=" + category + "&size=1&page=2")
                .then().statusCode(200).body("size()", is(0));
    }

    @Test
    @DisplayName("Filtering by a category that does not exist returns an empty list")
    void getProducts_unknownCategory_returnsEmpty() {
        // when / then
        given().when().get("/products?category_id=99999999&size=100")
                .then().statusCode(200).body("size()", is(0));
    }
}
