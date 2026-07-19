package the.chak.ecommerce.products.control;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.boundary.dto.ReviewRequest;
import the.chak.ecommerce.products.control.exceptions.NotReviewAuthorException;
import the.chak.ecommerce.products.control.exceptions.NotVerifiedPurchaserException;
import the.chak.ecommerce.products.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.products.control.exceptions.ReviewNotFoundException;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.entity.Review;
import the.chak.ecommerce.products.repository.ProductRepository;
import the.chak.ecommerce.products.repository.ReviewRepository;

/**
 * Reviews are restricted to verified purchasers (see docs/specs/product-reviews.md) and upsert on
 * (product, reviewer) - one review per reviewer per product, editable/deletable by its author.
 */
@ApplicationScoped
public class ReviewService {

    @Inject
    ReviewRepository reviewRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    OrdersApiClient ordersApiClient;

    public Review submitReview(String reviewer, ReviewRequest request) {
        if (!ordersApiClient.hasPurchased(reviewer, request.getProductId())) {
            throw new NotVerifiedPurchaserException(request.getProductId());
        }
        return upsertReview(UUID.fromString(request.getProductId()), reviewer, request);
    }

    @Transactional
    Review upsertReview(UUID productId, String reviewer, ReviewRequest request) {
        Product product = productRepository.findByUuid(productId);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        Review review = reviewRepository.findByProductAndReviewer(productId, reviewer)
                .orElseGet(Review::new);
        review.setProductId(productId);
        review.setReviewer(reviewer);
        review.setStars(request.getStars());
        review.setText(request.getText());
        reviewRepository.persist(review);
        recomputeAggregate(product);
        return review;
    }

    public void deleteReview(String reviewer, String reviewId) {
        Review review = findByIdOrThrow(reviewId);
        if (!review.getReviewer().equals(reviewer)) {
            throw new NotReviewAuthorException(reviewId);
        }
        removeReview(review);
    }

    @Transactional
    void removeReview(Review review) {
        reviewRepository.delete(review);
        Product product = productRepository.findByUuid(review.getProductId());
        if (product != null) {
            recomputeAggregate(product);
        }
    }

    public List<Review> listReviews(String productId, int pageIndex, int pageSize) {
        return reviewRepository.findByProduct(UUID.fromString(productId), pageIndex, pageSize);
    }

    private Review findByIdOrThrow(String reviewId) {
        try {
            return reviewRepository.findByUuidOptional(UUID.fromString(reviewId))
                    .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        } catch (IllegalArgumentException e) {
            throw new ReviewNotFoundException(reviewId);
        }
    }

    private void recomputeAggregate(Product product) {
        Object[] result = reviewRepository.aggregateForProduct(product.getUuid());
        Double avgStars = (Double) result[0];
        Long count = (Long) result[1];
        product.setRating(avgStars);
        product.setReviewCount(count == null ? 0 : count.intValue());
    }
}
