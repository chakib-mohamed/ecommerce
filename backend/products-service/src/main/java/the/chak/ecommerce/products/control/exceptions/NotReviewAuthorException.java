package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class NotReviewAuthorException extends FunctionalException {

    public NotReviewAuthorException(String reviewId) {
        super(Response.Status.FORBIDDEN, "NOT_REVIEW_AUTHOR",
                "Caller is not the author of review: " + reviewId);
    }
}
