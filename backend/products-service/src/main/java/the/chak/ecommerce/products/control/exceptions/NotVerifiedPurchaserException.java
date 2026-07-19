package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class NotVerifiedPurchaserException extends FunctionalException {

    public NotVerifiedPurchaserException(String productId) {
        super(Response.Status.FORBIDDEN, "NOT_VERIFIED_PURCHASER",
                "No qualifying order found for product: " + productId);
    }
}
