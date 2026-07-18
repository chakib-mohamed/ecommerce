package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
@Tag("integration")
class CategoriesResourceTest {

    private Integer createdCategoryId;

    /** LIFO so children (added last) are deleted before their parents. */
    private final Deque<Integer> toDelete = new ArrayDeque<>();

    @AfterEach
    void cleanup() {
        if (createdCategoryId != null) {
            given().when().delete("/categories/{id}", createdCategoryId);
            createdCategoryId = null;
        }
        while (!toDelete.isEmpty()) {
            given().when().delete("/categories/{id}", toDelete.pop());
        }
    }

    private Integer createCategory(String label, Integer parentId) {
        var body = parentId == null
                ? Map.<String, Object>of("label", label)
                : Map.<String, Object>of("label", label, "parent_id", parentId);
        Integer id = given().contentType(ContentType.JSON).body(body)
                .when().post("/categories").then().statusCode(201).extract().path("id");
        toDelete.push(id);
        return id;
    }

    static {
        RestAssured.filters(new RequestLoggingFilter(LogDetail.ALL),
                new ResponseLoggingFilter(LogDetail.ALL));
    }

    @Test
    @DisplayName("Returns 201 with the new id and label when the label is valid")
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
    @DisplayName("Returns a non-empty list when at least one category exists")
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
    @DisplayName("Returns 200 with the new label when updating an existing category")
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
    @DisplayName("Returns a single matching category when searching by exact label")
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
    @DisplayName("Returns 400 with CATEGORY_ALREADY_EXISTS when the label is already taken")
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
    @DisplayName("Returns 200 when deleting an existing category")
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
    @DisplayName("Returns 400 with VALIDATION_ERROR when creating a category with a blank label")
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
    @DisplayName("Returns 400 with VALIDATION_ERROR when creating a category with no label")
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
    @DisplayName("Returns 400 with VALIDATION_ERROR when updating a category without an id")
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
    @DisplayName("Returns 400 with VALIDATION_ERROR when updating a category to a blank label")
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

    @Test
    @DisplayName("Returns 201 with parent_id set when creating a subcategory under an existing parent")
    void createCategory_withValidParent_returns201WithParentId() {
        // given
        Integer parentId = createCategory("Parent-" + UUID.randomUUID(), null);

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", "Child-" + UUID.randomUUID(), "parent_id", parentId))
                .when().post("/categories");

        // then
        Integer childId = response.then().statusCode(201)
                .body("id", notNullValue())
                .body("parent_id", is(parentId))
                .extract().path("id");
        toDelete.push(childId);
    }

    @Test
    @DisplayName("Returns 400 with PARENT_CATEGORY_NOT_FOUND when creating under a parent that does not exist")
    void createCategory_unknownParent_returns400() {
        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("label", "Orphan-" + UUID.randomUUID(), "parent_id", 999_999_999))
                .when().post("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("PARENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("Returns 200 with the new parent_id when re-parenting a category on update")
    void updateCategory_reParent_returns200WithNewParent() {
        // given
        Integer parentA = createCategory("ParentA-" + UUID.randomUUID(), null);
        Integer parentB = createCategory("ParentB-" + UUID.randomUUID(), null);
        Integer childId = createCategory("Child-" + UUID.randomUUID(), parentA);

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("id", childId, "label", "Child-Renamed", "parent_id", parentB))
                .when().put("/categories");

        // then
        response.then().statusCode(200)
                .body("label", is("Child-Renamed"))
                .body("parent_id", is(parentB));
    }

    @Test
    @DisplayName("Returns 400 with INVALID_CATEGORY_PARENT when a category is set as its own parent")
    void updateCategory_selfParent_returns400() {
        // given
        Integer categoryId = createCategory("SelfParent-" + UUID.randomUUID(), null);

        // when
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("id", categoryId, "label", "Self", "parent_id", categoryId))
                .when().put("/categories");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("INVALID_CATEGORY_PARENT"));
    }

    @Test
    @DisplayName("Returns 409 with CATEGORY_HAS_CHILDREN when deleting a category that still has subcategories")
    void deleteCategory_withChildren_returns409() {
        // given
        Integer parentId = createCategory("ParentWithChild-" + UUID.randomUUID(), null);
        createCategory("Child-" + UUID.randomUUID(), parentId);

        // when
        var response = given().when().delete("/categories/{id}", parentId);

        // then
        response.then().statusCode(409)
                .body("type", is("FUNCTIONAL"))
                .body("errorCode", is("CATEGORY_HAS_CHILDREN"));
    }
}
