package the.chak.ecommerce.products.control;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
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

    // -- submitReview: the verified-purchaser path --------------------------

    @Test
    @DisplayName("Stores the review and recomputes the product's rating for a verified purchaser")
    void submitReview_verifiedPurchaser_storesReviewAndRecomputesRating() {
        // given
        UUID productUuid = UUID.randomUUID();
        Product product = new Product();
        product.setUuid(productUuid);
        when(ordersApiClient.hasPurchased("shopper", productUuid.toString())).thenReturn(true);
        when(productRepository.findByUuid(productUuid)).thenReturn(product);
        when(reviewRepository.findByProductAndReviewer(productUuid, "shopper"))
                .thenReturn(Optional.empty());
        when(reviewRepository.aggregateForProduct(productUuid))
                .thenReturn(new Object[]{4.5d, 2L});

        ReviewRequest request = new ReviewRequest();
        request.setProductId(productUuid.toString());
        request.setStars(5);

        // when
        Review saved = reviewService.submitReview("shopper", request);

        // then
        assertEquals("shopper", saved.getReviewer());
        assertEquals(4.5d, product.getRating());
        assertEquals(2, product.getReviewCount());
    }

    @Test
    @DisplayName("Throws ProductNotFoundException when reviewing a product that no longer exists")
    void submitReview_productRemoved_throwsProductNotFoundException() {
        // given - the order history still names a product the catalog has since dropped
        UUID productUuid = UUID.randomUUID();
        when(ordersApiClient.hasPurchased("shopper", productUuid.toString())).thenReturn(true);
        when(productRepository.findByUuid(productUuid)).thenReturn(null);

        ReviewRequest request = new ReviewRequest();
        request.setProductId(productUuid.toString());
        request.setStars(4);

        // when / then
        assertThrows(ProductNotFoundException.class,
                () -> reviewService.submitReview("shopper", request));
    }

    @Test
    @DisplayName("Reports no rating when the last review of a product is removed")
    void submitReview_noReviewsRemain_clearsTheCount() {
        // given - the aggregate query returns nulls when nothing is left to average
        UUID productUuid = UUID.randomUUID();
        Product product = new Product();
        product.setUuid(productUuid);
        when(ordersApiClient.hasPurchased("shopper", productUuid.toString())).thenReturn(true);
        when(productRepository.findByUuid(productUuid)).thenReturn(product);
        when(reviewRepository.findByProductAndReviewer(productUuid, "shopper"))
                .thenReturn(Optional.empty());
        when(reviewRepository.aggregateForProduct(productUuid))
                .thenReturn(new Object[]{null, null});

        ReviewRequest request = new ReviewRequest();
        request.setProductId(productUuid.toString());
        request.setStars(3);

        // when
        reviewService.submitReview("shopper", request);

        // then - a null count reads as zero rather than propagating
        assertEquals(0, product.getReviewCount());
    }

    @Test
    @DisplayName("Leaves the aggregate alone when the reviewed product has since been removed")
    void deleteReview_productRemoved_skipsRecompute() {
        // given
        UUID reviewUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        Review review = new Review();
        review.setUuid(reviewUuid);
        review.setReviewer("owner");
        review.setProductId(productUuid);
        when(reviewRepository.findByUuidOptional(reviewUuid)).thenReturn(Optional.of(review));
        when(productRepository.findByUuid(productUuid)).thenReturn(null);

        // when
        reviewService.deleteReview("owner", reviewUuid.toString());

        // then
        verify(reviewRepository).delete(review);
        verify(reviewRepository, never()).aggregateForProduct(productUuid);
    }
}
