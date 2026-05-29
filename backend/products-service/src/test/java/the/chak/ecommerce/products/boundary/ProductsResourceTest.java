package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class ProductsResourceTest {

    private String createdProductUuid;

    @AfterEach
    void cleanup() {
        if (createdProductUuid != null) {
            given().when().delete("/products/{id}", createdProductUuid);
            createdProductUuid = null;
        }
    }

    static final String BASE64_IMAGE =
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    static {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @Test
    void createProduct_validRequest_returns201WithAllFields() {
        // given
        Map<String, Object> request = Map.of(
                "title", "Integration Test Product",
                "description", "Created by RestAssured",
                "price", 123.45,
                "image", BASE64_IMAGE);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/products");

        // then
        createdProductUuid = response.then().statusCode(201)
                .body("title", is("Integration Test Product"))
                .body("description", is("Created by RestAssured"))
                .body("price", is(123.45f))
                .body("image_key", notNullValue())
                .body("uuid", notNullValue())
                .extract().path("uuid");
    }

    @Test
    void getProduct_existingProduct_returnsAllFields() {
        // given
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", "Get Test Product", "description", "desc",
                        "price", 10.0, "image", BASE64_IMAGE))
                .when().post("/products");
        
        createdProductUuid = createResponse.then().statusCode(201).extract().path("uuid");

        // when
        var response = given().when().get("/products/{id}", createdProductUuid);

        // then
        response.then().statusCode(200)
                .body("uuid", is(createdProductUuid))
                .body("title", is("Get Test Product"))
                .body("description", is("desc"))
                .body("price", is(10.0f))
                .body("image_key", notNullValue())
                .body("categories", notNullValue())
                .body("promotions", notNullValue());
    }

    @Test
    void listProducts_withExistingProduct_returnsNonEmptyList() {
        // given
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", "List Test Product", "description", "desc",
                        "price", 5.0, "image", BASE64_IMAGE))
                .when().post("/products");
        
        createdProductUuid = createResponse.then().statusCode(201).extract().path("uuid");

        // when
        var response = given().when().get("/products");

        // then
        response.then().statusCode(200).body("size()", greaterThan(0));
    }

    @Test
    void deleteProduct_existingProduct_removesProduct() {
        // given
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", "Delete Test Product", "description", "desc",
                        "price", 5.0, "image", BASE64_IMAGE))
                .when().post("/products");
        
        String productUuid = createResponse.then().statusCode(201).extract().path("uuid");

        // when
        var response = given().when().delete("/products/{id}", productUuid);

        // then
        response.then().statusCode(200);
        given().when().get("/products/{id}", productUuid).then().statusCode(404);
    }

    @Test
    void updateProduct_existingProduct_returns200WithUpdatedFields() {
        // given
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", "To Be Updated", "description", "Original description",
                        "price", 10.0, "image", BASE64_IMAGE))
                .when().post("/products");
        
        createdProductUuid = createResponse.then().statusCode(201).extract().path("uuid");

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("uuid", createdProductUuid, "title", "Updated Title",
                        "description", "Updated description", "price", 20.0, "image", BASE64_IMAGE))
                .when().put("/products");

        // then
        response.then().statusCode(200)
                .body("title", is("Updated Title"))
                .body("price", is(20.0f));
        
        given().when().get("/products/{id}", createdProductUuid).then().statusCode(200)
                .body("title", is("Updated Title"))
                .body("price", is(20.0f));
    }

    @Test
    void searchProducts_exactTitleMatch_returnsSingleResult() {
        // given
        String title = "Searchable Product " + UUID.randomUUID();
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", title, "description", "Searchable description", "price", 50.0))
                .when().post("/products");
        
        createdProductUuid = createResponse.then().statusCode(201).extract().path("uuid");

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("title", Map.of("operator", "EQUALS", "value", title)))
                .when().post("/products/search");

        // then
        response.then().statusCode(200)
                .body("size()", is(1))
                .body("[0].title", is(title))
                .body("[0].uuid", is(createdProductUuid));
    }

    @Test
    void createProduct_invalidImageFormat_returns400() {
        // given
        String invalidImage = "VGhpcyBpcyBub3QgYW4gaW1hZ2U="; // "This is not an image"
        Map<String, Object> request = Map.of(
                "title", "Invalid Image Product",
                "description", "Created by RestAssured",
                "price", 123.45,
                "image", invalidImage);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/products");

        // then
        response.then().statusCode(400);
    }

    @Test
    void getImage_uploadedProduct_returnsImageBytes() {
        // given
        var createResponse = given().contentType(ContentType.JSON)
                .body(Map.of("title", "Image Test Product", "description", "Testing image retrieval",
                        "price", 9.99, "image", BASE64_IMAGE))
                .when().post("/products").then().statusCode(201).extract();
        createdProductUuid = createResponse.path("uuid");
        String imageKey = createResponse.path("image_key");

        // when
        var response = given().when().get("/products/images/{imageKey}", imageKey);

        // then
        byte[] downloadedImage = response.then().statusCode(200)
                .contentType("image/jpeg")
                .extract().asByteArray();
        assertNotNull(downloadedImage);
        assertTrue(downloadedImage.length > 0);
    }
}
