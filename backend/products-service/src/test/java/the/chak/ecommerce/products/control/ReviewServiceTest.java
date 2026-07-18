package the.chak.ecommerce.products.control;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.boundary.dto.ReviewRequest;
import the.chak.ecommerce.products.control.exceptions.NotReviewAuthorException;
import the.chak.ecommerce.products.control.exceptions.NotVerifiedPurchaserException;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.entity.Review;
import the.chak.ecommerce.products.repository.ProductRepository;
import the.chak.ecommerce.products.repository.ReviewRepository;

/**
 * Pins ReviewService's intended business rules ahead of implementation (see
 * docs/specs/product-reviews.md): the verified-purchaser gate and author-only delete.
 * Reviews are addressed by their public uuid, matching how Product is addressed elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    ReviewRepository reviewRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    OrdersApiClient ordersApiClient;

    @InjectMocks
    ReviewService reviewService;

    @Test
    @DisplayName("Throws NotVerifiedPurchaserException when the reviewer has no qualifying order for the product")
    void submitReview_reviewerHasNotPurchased_throwsNotVerifiedPurchaserException() {
        // given
        String productId = "b0000000-0000-0000-0000-000000000901";
        when(ordersApiClient.hasPurchased("shopper", productId)).thenReturn(false);
        ReviewRequest request = new ReviewRequest();
        request.setProductId(productId);
        request.setStars(4);

        // when & then
        assertThrows(NotVerifiedPurchaserException.class,
                () -> reviewService.submitReview("shopper", request));
    }

    @Test
    @DisplayName("Throws NotReviewAuthorException when a non-author tries to delete a review")
    void deleteReview_callerIsNotAuthor_throwsNotReviewAuthorException() {
        // given
        UUID reviewUuid = UUID.randomUUID();
        Review review = new Review();
        review.setReviewer("original_author");
        when(reviewRepository.findByUuidOptional(reviewUuid)).thenReturn(Optional.of(review));

        // when & then
        assertThrows(NotReviewAuthorException.class,
                () -> reviewService.deleteReview("someone_else", reviewUuid.toString()));
    }

    @Test
    @DisplayName("Deletes the review and recomputes the product aggregate when the caller is its author")
    void deleteReview_callerIsAuthor_removesReview() {
        // given
        UUID reviewUuid = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Review review = new Review();
        review.setReviewer("owner");
        review.setProductId(productId);
        when(reviewRepository.findByUuidOptional(reviewUuid)).thenReturn(Optional.of(review));
        when(productRepository.findByUuid(productId)).thenReturn(new Product());
        when(reviewRepository.aggregateForProduct(any())).thenReturn(new Object[] { null, 0L });

        // when
        reviewService.deleteReview("owner", reviewUuid.toString());

        // then
        verify(reviewRepository).delete(review);
    }
}
