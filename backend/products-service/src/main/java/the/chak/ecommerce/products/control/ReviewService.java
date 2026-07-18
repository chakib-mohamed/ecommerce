package the.chak.ecommerce.products.control;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.boundary.dto.ReviewRequest;
import the.chak.ecommerce.products.entity.Review;
import the.chak.ecommerce.products.repository.ReviewRepository;

/**
 * Reviews are restricted to verified purchasers (see docs/specs/product-reviews.md) and upsert on
 * (product, reviewer) — one review per reviewer per product, editable/deletable by its author.
 * Not yet implemented; this is the failing-tests checkpoint before implementation.
 */
@ApplicationScoped
public class ReviewService {

    @Inject
    ReviewRepository reviewRepository;

    @Inject
    OrdersApiClient ordersApiClient;

    public Review submitReview(String reviewer, ReviewRequest request) {
        throw new UnsupportedOperationException("ReviewService.submitReview not implemented yet");
    }

    public void deleteReview(String reviewer, String reviewId) {
        throw new UnsupportedOperationException("ReviewService.deleteReview not implemented yet");
    }

    public List<Review> listReviews(String productId, int pageIndex, int pageSize) {
        throw new UnsupportedOperationException("ReviewService.listReviews not implemented yet");
    }
}
