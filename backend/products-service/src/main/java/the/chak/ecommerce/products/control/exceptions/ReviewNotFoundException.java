package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class ReviewNotFoundException extends FunctionalException {

    public ReviewNotFoundException(String reviewId) {
        super(Response.Status.NOT_FOUND, "REVIEW_NOT_FOUND", "Review not found: " + reviewId);
    }
}
