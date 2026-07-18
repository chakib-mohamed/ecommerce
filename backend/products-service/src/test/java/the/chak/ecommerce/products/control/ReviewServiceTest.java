package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.boundary.dto.ReviewRequest;
import the.chak.ecommerce.products.control.exceptions.NotReviewAuthorException;
import the.chak.ecommerce.products.control.exceptions.NotVerifiedPurchaserException;
import the.chak.ecommerce.products.entity.Review;
import the.chak.ecommerce.products.repository.ReviewRepository;

/**
 * Pins ReviewService's intended business rules ahead of implementation (see
 * docs/specs/product-reviews.md): the verified-purchaser gate and author-only delete.
 * ReviewService currently stubs every method with UnsupportedOperationException, so these
 * fail until implemented.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    ReviewRepository reviewRepository;

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
        Review review = new Review();
        review.setReviewer("original_author");
        when(reviewRepository.findByIdOptional(1L)).thenReturn(Optional.of(review));

        // when & then
        assertThrows(NotReviewAuthorException.class,
                () -> reviewService.deleteReview("someone_else", "1"));
    }

    @Test
    @DisplayName("Deletes the review and recomputes the product aggregate when the caller is its author")
    void deleteReview_callerIsAuthor_removesReview() {
        // given
        Review review = new Review();
        review.setId(1L);
        review.setReviewer("owner");
        when(reviewRepository.findByIdOptional(1L)).thenReturn(Optional.of(review));

        // when
        reviewService.deleteReview("owner", "1");

        // then
        verify(reviewRepository).delete(review);
    }
}
