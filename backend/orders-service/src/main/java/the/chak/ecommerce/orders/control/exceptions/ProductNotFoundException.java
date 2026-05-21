package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;

public class ProductNotFoundException extends FunctionalException {

    public ProductNotFoundException(String productId) {
        super(Response.Status.BAD_REQUEST, "PRODUCT_NOT_FOUND", "Product not found: " + productId);
    }
}
