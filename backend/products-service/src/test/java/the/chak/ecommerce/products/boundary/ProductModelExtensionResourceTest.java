package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

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
 * Covers the product-model-extension contract: real {@code stock}, the exposed category tree
 * ({@code parent_id} + nested {@code sub_categories}, roots only on the list), and the
 * {@code category_id}/{@code subcategory_id} derivation and write-path resolution.
 *
 * <p>Relies on the test-only fixture in {@code import-test.sql}: category 900 'TestParent'
 * (top-level), category 901 'TestChild' (child of 900), and product 900 filed under 901.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class ProductModelExtensionResourceTest {

    private static final String SEEDED_LEAF_PRODUCT = "b0000000-0000-0000-0000-000000000901";
    private static final int PARENT_ID = 900;
    private static final int CHILD_ID = 901;

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

    // -- category tree exposure -------------------------------------------------

    @Test
    @DisplayName("Lists only top-level categories, each carrying its nested sub_categories")
    void getCategories_returnsRootsOnly_withNestedChildren() {
        // when
        var response = given().when().get("/categories");

        // then
        response.then().statusCode(200)
                .body("parent_id", everyItem(nullValue()))
                .body("label", hasItem("TestParent"))
                .body("label", not(hasItem("TestChild")))
                .body("find { it.label == 'TestParent' }.sub_categories.label", hasItems("TestChild"));
    }

    @Test
    @DisplayName("Exposes the parent id on a child nested under a top-level category")
    void getCategories_nestedChild_carriesParentId() {
        // when
        var response = given().when().get("/categories");

        // then
        response.then().statusCode(200)
                .body("find { it.label == 'TestParent' }.sub_categories"
                        + ".find { it.label == 'TestChild' }.parent_id", is(PARENT_ID));
    }

    // -- read-side derivation ---------------------------------------------------

    @Test
    @DisplayName("Derives category_id from the parent and subcategory_id from the leaf for a filed product")
    void getProduct_leafFiledProduct_returnsParentAndLeafIds() {
        // when
        var response = given().when().get("/products/{id}", SEEDED_LEAF_PRODUCT);

        // then
        response.then().statusCode(200)
                .body("category_id", is(PARENT_ID))
                .body("subcategory_id", is(CHILD_ID));
    }

    // -- stock ------------------------------------------------------------------

    @Test
    @DisplayName("Returns 201 echoing the stock and persists it for retrieval")
    void createProduct_withStock_returns201WithStockAndPersists() {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Stocked Product " + UUID.randomUUID());
        request.put("description", "has stock");
        request.put("price", 49.99);
        request.put("stock", 7);

        // when
        String uuid = given().contentType(ContentType.JSON).body(request)
                .when().post("/products")
                .then().statusCode(201)
                .body("stock", is(7))
                .extract().path("uuid");
        createdProductUuids.add(uuid);

        // then
        given().when().get("/products/{id}", uuid).then().statusCode(200)
                .body("stock", is(7));
    }

    @Test
    @DisplayName("Returns 400 when creating a product with a negative stock")
    void createProduct_negativeStock_returns400() {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Bad Stock " + UUID.randomUUID());
        request.put("price", 10.0);
        request.put("stock", -1);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/products")
                .then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Updates the stock and persists the new value for an existing product")
    void updateProduct_changeStock_persistsNewStock() {
        // given
        Map<String, Object> create = new HashMap<>();
        create.put("title", "Stock Update " + UUID.randomUUID());
        create.put("price", 10.0);
        create.put("stock", 3);
        String uuid = given().contentType(ContentType.JSON).body(create)
                .when().post("/products").then().statusCode(201).extract().path("uuid");
        createdProductUuids.add(uuid);

        Map<String, Object> update = new HashMap<>();
        update.put("uuid", uuid);
        update.put("title", "Stock Update");
        update.put("price", 10.0);
        update.put("stock", 12);

        // when
        given().contentType(ContentType.JSON).body(update)
                .when().put("/products").then().statusCode(200)
                .body("stock", is(12));

        // then
        given().when().get("/products/{id}", uuid).then().statusCode(200)
                .body("stock", is(12));
    }

    // -- write-path resolution --------------------------------------------------

    @Test
    @DisplayName("Files a product under the given subcategory and returns the parent and leaf ids")
    void createProduct_withCategoryAndSubcategory_linksAndReturnsIds() {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Categorised Product " + UUID.randomUUID());
        request.put("price", 59.99);
        request.put("category_id", PARENT_ID);
        request.put("subcategory_id", CHILD_ID);

        // when
        String uuid = given().contentType(ContentType.JSON).body(request)
                .when().post("/products").then().statusCode(201).extract().path("uuid");
        createdProductUuids.add(uuid);

        // then
        given().when().get("/products/{id}", uuid).then().statusCode(200)
                .body("category_id", is(PARENT_ID))
                .body("subcategory_id", is(CHILD_ID))
                .body("categories.id", hasItem(CHILD_ID));
    }

    @Test
    @DisplayName("Omits subcategory_id when a product is filed directly under a top-level category")
    void createProduct_topLevelCategoryOnly_omitsSubcategoryId() {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Top Level Product " + UUID.randomUUID());
        request.put("price", 39.99);
        request.put("category_id", PARENT_ID);

        // when
        String uuid = given().contentType(ContentType.JSON).body(request)
                .when().post("/products").then().statusCode(201).extract().path("uuid");
        createdProductUuids.add(uuid);

        // then
        given().when().get("/products/{id}", uuid).then().statusCode(200)
                .body("category_id", is(PARENT_ID))
                .body("subcategory_id", nullValue())
                .body("categories.id", hasItem(PARENT_ID));
    }
}
