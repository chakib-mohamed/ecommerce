package the.chak.ecommerce.products.boundary;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import the.chak.ecommerce.products.MinioTestResource;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductsResourceTest {

        static {
                RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                                new ResponseLoggingFilter(LogDetail.ALL));
        }

        @Test
        @Order(1)
        public void testCreateAndListProducts() {
                // Create
                // Minimal 1x1 GIF
                String base64Image = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
                Map<String, Object> newProduct = Map.of("title", "Integration Test Product",
                                "description", "Created by RestAssured", "price", 123.45, "image",
                                base64Image);

                var response = given().contentType(ContentType.JSON).body(newProduct).when()
                                .post("/products").then().statusCode(201)
                                .body("title", is("Integration Test Product"))
                                .body("description", is("Created by RestAssured"))
                                .body("price", is(123.45f))
                                .body("image_key", is(org.hamcrest.Matchers.notNullValue()))
                                .body("uuid", is(org.hamcrest.Matchers.notNullValue())).extract();

                String productUuid = response.path("uuid");

                // Get by ID - verify all fields persisted
                given().when().get("/products/{id}", productUuid).then().statusCode(200)
                                .body("uuid", is(productUuid))
                                .body("title", is("Integration Test Product"))
                                .body("description", is("Created by RestAssured"))
                                .body("price", is(123.45f))
                                .body("image_key", is(org.hamcrest.Matchers.notNullValue()))
                                .body("categories", is(org.hamcrest.Matchers.notNullValue()))
                                .body("promotions", is(org.hamcrest.Matchers.notNullValue()));

                // List
                given().when().get("/products").then().statusCode(200).body("size()",
                                greaterThan(0));

                // Delete
                given().when().delete("/products/{id}", productUuid).then().statusCode(200);

                // Verify Delete
                given().when().get("/products/{id}", productUuid).then().statusCode(404);
        }

        @Test
        @Order(2)
        public void testUpdateProduct() {
                // Create
                String base64Image = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
                Map<String, Object> newProduct = Map.of("title", "To Be Updated", "description",
                                "Original description", "price", 10.0, "image", base64Image);

                var response = given().contentType(ContentType.JSON).body(newProduct).when()
                                .post("/products").then().statusCode(201).extract();

                String productUuid = response.path("uuid");

                // Update
                Map<String, Object> updatedProduct = Map.of("uuid", productUuid, "title",
                                "Updated Title", "description", "Updated description", "price",
                                20.0, "image", base64Image);

                given().contentType(ContentType.JSON).body(updatedProduct).when().put("/products")
                                .then().statusCode(200).body("title", is("Updated Title"))
                                .body("price", is(20.0f));

                // Verify update with GET
                given().when().get("/products/{id}", productUuid).then().statusCode(200)
                                .body("title", is("Updated Title")).body("price", is(20.0f));

                // Cleanup
                given().when().delete("/products/{id}", productUuid).then().statusCode(200);
        }

        @Test
        @Order(3)
        public void testSearchProducts() {
                // Create a product to search for
                String title = "Searchable Product " + UUID.randomUUID().toString();
                Map<String, Object> newProduct = Map.of("title", title, "description",
                                "Searchable description", "price", 50.0);

                var response = given().contentType(ContentType.JSON).body(newProduct).when()
                                .post("/products").then().statusCode(201).extract();

                String productUuid = response.path("uuid");

                // Search by exact title
                Map<String, Object> searchCriteria =
                                Map.of("title", Map.of("operator", "EQUALS", "value", title));

                given().contentType(ContentType.JSON).body(searchCriteria).when()
                                .post("/products/search").then().statusCode(200)
                                .body("size()", is(1)).body("[0].title", is(title))
                                .body("[0].uuid", is(productUuid));

                // Cleanup
                given().when().delete("/products/{id}", productUuid).then().statusCode(200);
        }

        @Test
        @Order(4)
        public void testCreateProductWithInvalidImageFormat() {
                String invalidImage = "VGhpcyBpcyBub3QgYW4gaW1hZ2U="; // "This is not an image"
                Map<String, Object> newProduct = Map.of("title", "Invalid Image Product",
                                "description", "Created by RestAssured", "price", 123.45, "image",
                                invalidImage);

                given().contentType(ContentType.JSON).body(newProduct).when().post("/products")
                                .then().statusCode(400);
        }

        @Test
        @Order(5)
        public void testGetImage() {
                // 1. Create product with image
                String base64Image = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
                Map<String, Object> newProduct = Map.of("title", "Image Test Product",
                                "description", "Testing image retrieval", "price", 9.99, "image",
                                base64Image);

                var response = given().contentType(ContentType.JSON).body(newProduct).when()
                                .post("/products").then().statusCode(201).extract();

                String productUuid = response.path("uuid");
                String imageKey = response.path("image_key");

                // 2. Retrieve image
                byte[] downloadedImage = given().when().get("/products/images/{imageKey}", imageKey)
                                .then().statusCode(200).contentType("image/jpeg").extract()
                                .asByteArray();

                // 3. Verify content (basic check)
                org.junit.jupiter.api.Assertions.assertNotNull(downloadedImage);
                org.junit.jupiter.api.Assertions.assertTrue(downloadedImage.length > 0);

                // Cleanup
                given().when().delete("/products/{id}", productUuid).then().statusCode(200);
        }
}
