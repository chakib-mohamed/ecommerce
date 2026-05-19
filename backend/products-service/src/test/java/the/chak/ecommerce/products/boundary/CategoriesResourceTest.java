package the.chak.ecommerce.products.boundary;

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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CategoriesResourceTest {

        static {
                RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                                new ResponseLoggingFilter(LogDetail.ALL));
        }

        @Test
        @Order(1)
        public void testCreateCategory() {
                Map<String, String> category = Map.of("label", "Test Category");

                given().contentType(ContentType.JSON).body(category).when().post("/categories")
                                .then().statusCode(201).body("label", is("Test Category"))
                                .body("id", notNullValue());
        }

        @Test
        @Order(2)
        public void testGetCategories() {
                given().when().get("/categories").then().statusCode(200).body("size()",
                                notNullValue());
        }

        @Test
        @Order(3)
        public void testUpdateCategory() {
                // Create
                Map<String, String> category = Map.of("label", "To Be Updated");
                var response = given().contentType(ContentType.JSON).body(category).when()
                                .post("/categories").then().statusCode(201).extract();

                Integer id = response.path("id");

                // Update
                Map<String, Object> updatedCategory =
                                Map.of("id", id, "label", "Updated Category Label");

                given().contentType(ContentType.JSON).body(updatedCategory).when()
                                .put("/categories").then().statusCode(200)
                                .body("label", is("Updated Category Label"));

                // Cleanup
                given().when().delete("/categories/{categoryID}", id).then().statusCode(200);
        }

        @Test
        @Order(4)
        public void testSearchCategories() {
                // Create
                String label = "Searchable " + java.util.UUID.randomUUID().toString();
                Map<String, String> category = Map.of("label", label);
                var response = given().contentType(ContentType.JSON).body(category).when()
                                .post("/categories").then().statusCode(201).extract();

                Integer id = response.path("id");

                // Search
                Map<String, Object> searchCriteria =
                                Map.of("label", Map.of("operator", "EQUALS", "value", label));

                given().contentType(ContentType.JSON).body(searchCriteria).when()
                                .post("/categories/search").then().statusCode(200)
                                .body("size()", is(1)).body("[0].label", is(label));

                // Cleanup
                given().when().delete("/categories/{categoryID}", id).then().statusCode(200);
        }

        @Test
        @Order(5)
        public void testCreateDuplicateCategory() {
                Map<String, String> category = Map.of("label", "Test Category");

                given().contentType(ContentType.JSON).body(category).when().post("/categories")
                                .then().statusCode(400);
        }

        @Test
        @Order(4)
        public void testDeleteCategory() {
                // First create a category to delete
                Map<String, String> category = Map.of("label", "To Be Deleted");
                var response = given().contentType(ContentType.JSON).body(category).when()
                                .post("/categories").then().statusCode(201).extract();

                Integer id = response.path("id");

                given().when().delete("/categories/{categoryID}", id).then().statusCode(200);
        }
}
