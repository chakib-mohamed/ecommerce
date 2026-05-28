package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StorageTestResource.class)
class CategoriesResourceTest {

    private Integer createdCategoryId;

    @AfterEach
    void cleanup() {
        if (createdCategoryId != null) {
            given().when().delete("/categories/{id}", createdCategoryId);
            createdCategoryId = null;
        }
    }

    static {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @Test
    void createCategory_validLabel_returns201WithIdAndLabel() {
        // given
        String label = "Category-" + UUID.randomUUID();

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", label))
                .when().post("/categories");

        // then
        createdCategoryId = response.then().statusCode(201)
                .body("label", is(label))
                .body("id", notNullValue())
                .extract().path("id");
    }

    @Test
    void getCategories_existingCategory_returnsNonEmptyList() {
        // given
        createdCategoryId = given().contentType(ContentType.JSON)
                .body(Map.of("label", "Category-" + UUID.randomUUID()))
                .when().post("/categories").then().statusCode(201).extract().path("id");

        // when
        var response = given().when().get("/categories");

        // then
        response.then().statusCode(200).body("size()", greaterThan(0));
    }

    @Test
    void updateCategory_existingCategory_returns200WithNewLabel() {
        // given
        createdCategoryId = given().contentType(ContentType.JSON)
                .body(Map.of("label", "To Be Updated-" + UUID.randomUUID()))
                .when().post("/categories").then().statusCode(201).extract().path("id");

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("id", createdCategoryId, "label", "Updated Category Label"))
                .when().put("/categories");

        // then
        response.then().statusCode(200).body("label", is("Updated Category Label"));
    }

    @Test
    void searchCategories_exactLabelMatch_returnsSingleResult() {
        // given
        String label = "Searchable-" + UUID.randomUUID();
        createdCategoryId = given().contentType(ContentType.JSON)
                .body(Map.of("label", label))
                .when().post("/categories").then().statusCode(201).extract().path("id");

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", Map.of("operator", "EQUALS", "value", label)))
                .when().post("/categories/search");

        // then
        response.then().statusCode(200)
                .body("size()", is(1))
                .body("[0].label", is(label));
    }

    @Test
    void createCategory_duplicateLabel_returns400() {
        // given
        String label = "Duplicate-" + UUID.randomUUID();
        createdCategoryId = given().contentType(ContentType.JSON)
                .body(Map.of("label", label))
                .when().post("/categories").then().statusCode(201).extract().path("id");

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", label))
                .when().post("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void deleteCategory_existingCategory_returns200() {
        // given
        Integer id = given().contentType(ContentType.JSON)
                .body(Map.of("label", "To Be Deleted-" + UUID.randomUUID()))
                .when().post("/categories").then().statusCode(201).extract().path("id");

        // when
        var response = given().when().delete("/categories/{id}", id);

        // then
        response.then().statusCode(200);
    }

    @Test
    void createCategory_blankLabel_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", ""))
                .when().post("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void createCategory_nullLabel_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body("{}")
                .when().post("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void updateCategory_nullId_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", "Valid Label"))
                .when().put("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }

    @Test
    void updateCategory_blankLabel_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("id", 1, "label", ""))
                .when().put("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("VALIDATION_ERROR"));
    }
}
