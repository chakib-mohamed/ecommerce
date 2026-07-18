package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.control.OrdersApiClient;
import the.chak.ecommerce.products.entity.Review;
import the.chak.ecommerce.products.repository.ReviewRepository;

/**
 * Covers the reviews contract from docs/specs/product-reviews.md: verified-purchaser gating,
 * one upsertable review per (product, reviewer), author-only delete, and the rating/review_count
 * aggregate surfaced on the product. Business logic (ReviewService) is not implemented yet -
 * these tests are the failing-tests checkpoint per CLAUDE.md's workflow.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class ReviewsResourceTest {

    private static final String SEEDED_LEAF_PRODUCT = "b0000000-0000-0000-0000-000000000901";

    @InjectMock
    OrdersApiClient ordersApiClient;

    @Inject
    ReviewRepository reviewRepository;

    @BeforeEach
    @Transactional
    void cleanup() {
        reviewRepository.deleteAll();
    }

    // -- listing -------------------------------------------------------------

    @Test
    @DisplayName("Returns an empty list when a product has no reviews")
    void getReviews_noReviews_returnsEmptyList() {
        // when
        var response = given().queryParam("product_id", SEEDED_LEAF_PRODUCT)
                .when().get("/reviews");

        // then
        response.then().statusCode(200).body("$", empty());
    }

    // -- submission: verified-purchaser gate ----------------------------------

    @Test
    @TestSecurity(user = "verified_buyer")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "verified_buyer") })
    @DisplayName("Returns 200 and persists the review when the reviewer has a qualifying order")
    void submitReview_verifiedPurchaser_returns200AndPersistsReview() {
        // given
        when(ordersApiClient.hasPurchased("verified_buyer", SEEDED_LEAF_PRODUCT)).thenReturn(true);
        Map<String, Object> request = new HashMap<>();
        request.put("product_id", SEEDED_LEAF_PRODUCT);
        request.put("stars", 5);
        request.put("text", "Great product");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/reviews");

        // then
        response.then().statusCode(200)
                .body("product_id", is(SEEDED_LEAF_PRODUCT))
                .body("reviewer", is("verified_buyer"))
                .body("stars", is(5));
    }

    @Test
    @TestSecurity(user = "window_shopper")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "window_shopper") })
    @DisplayName("Returns 403 with NOT_VERIFIED_PURCHASER when the reviewer has no qualifying order")
    void submitReview_notPurchaser_returns403() {
        // given
        when(ordersApiClient.hasPurchased("window_shopper", SEEDED_LEAF_PRODUCT)).thenReturn(false);
        Map<String, Object> request = new HashMap<>();
        request.put("product_id", SEEDED_LEAF_PRODUCT);
        request.put("stars", 4);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/reviews");

        // then
        response.then().statusCode(403)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("NOT_VERIFIED_PURCHASER"));
    }

    @Test
    @DisplayName("Returns 401 when submitting a review without authentication")
    void submitReview_noAuth_returns401() {
        // given
        Map<String, Object> request = new HashMap<>();
        request.put("product_id", SEEDED_LEAF_PRODUCT);
        request.put("stars", 3);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/reviews")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "verified_buyer")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "verified_buyer") })
    @DisplayName("Returns 400 with VALIDATION_ERROR when stars is outside 1-5")
    void submitReview_starsOutOfRange_returns400() {
        // given
        when(ordersApiClient.hasPurchased(any(), any())).thenReturn(true);
        Map<String, Object> request = new HashMap<>();
        request.put("product_id", SEEDED_LEAF_PRODUCT);
        request.put("stars", 6);

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/reviews");

        // then
        response.then().statusCode(400).body("type", is("FUNCTIONAL"));
    }

    @Test
    @TestSecurity(user = "repeat_buyer")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "repeat_buyer") })
    @DisplayName("Updates the existing review instead of creating a second one when the reviewer submits again for the same product")
    void submitReview_secondSubmissionSameProduct_upsertsExistingReview() {
        // given
        when(ordersApiClient.hasPurchased("repeat_buyer", SEEDED_LEAF_PRODUCT)).thenReturn(true);
        Map<String, Object> first = new HashMap<>();
        first.put("product_id", SEEDED_LEAF_PRODUCT);
        first.put("stars", 2);
        first.put("text", "Meh at first");
        given().contentType(ContentType.JSON).body(first).when().post("/reviews");

        Map<String, Object> second = new HashMap<>();
        second.put("product_id", SEEDED_LEAF_PRODUCT);
        second.put("stars", 5);
        second.put("text", "Grew on me");

        // when
        given().contentType(ContentType.JSON).body(second).when().post("/reviews")
                .then().statusCode(200).body("stars", is(5));

        // then
        assertTrue(reviewRepository.find(
                "productId = ?1 and reviewer = ?2",
                UUID.fromString(SEEDED_LEAF_PRODUCT), "repeat_buyer").list().size() == 1);
    }

    // -- delete ---------------------------------------------------------------

    @Test
    @TestSecurity(user = "review_owner")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "review_owner") })
    @DisplayName("Returns 200 and removes the review when its author deletes it")
    void deleteReview_ownReview_removesIt() {
        // given
        Review review = seedReview("review_owner", 4);

        // when
        var response = given().when().delete("/reviews/" + review.getUuid());

        // then
        response.then().statusCode(200);
        assertTrue(reviewRepository.findById(review.id) == null);
    }

    @Test
    @TestSecurity(user = "someone_else")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "someone_else") })
    @DisplayName("Returns 403 with NOT_REVIEW_AUTHOR when a non-author tries to delete a review")
    void deleteReview_notAuthor_returns403() {
        // given
        Review review = seedReview("original_author", 4);

        // when
        var response = given().when().delete("/reviews/" + review.getUuid());

        // then
        response.then().statusCode(403)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("NOT_REVIEW_AUTHOR"));
    }

    // -- aggregate --------------------------------------------------------------

    @Test
    @TestSecurity(user = "aggregate_buyer")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "aggregate_buyer") })
    @DisplayName("Surfaces the new rating and review_count on the product after a review is submitted")
    void submitReview_verifiedPurchaser_updatesProductRatingAndReviewCount() {
        // given
        when(ordersApiClient.hasPurchased("aggregate_buyer", SEEDED_LEAF_PRODUCT)).thenReturn(true);
        Map<String, Object> request = new HashMap<>();
        request.put("product_id", SEEDED_LEAF_PRODUCT);
        request.put("stars", 4);

        // when
        given().contentType(ContentType.JSON).body(request).when().post("/reviews")
                .then().statusCode(200);

        // then
        given().when().get("/products/{id}", SEEDED_LEAF_PRODUCT).then().statusCode(200)
                .body("rating", is(4.0f))
                .body("review_count", is(1));
    }

    @Transactional
    Review seedReview(String reviewer, int stars) {
        Review review = new Review();
        review.setProductId(UUID.fromString(SEEDED_LEAF_PRODUCT));
        review.setReviewer(reviewer);
        review.setStars(stars);
        review.setCreatedAt(LocalDateTime.now());
        reviewRepository.persist(review);
        return review;
    }
}
