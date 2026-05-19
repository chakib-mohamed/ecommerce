package the.chak.ecommerce.orders.control;

import jakarta.ws.rs.core.Response;

public class CartNotFoundException extends FunctionalException {

    public CartNotFoundException(String userId) {
        super(Response.Status.NOT_FOUND, "CART_NOT_FOUND", "Cart not found for user: " + userId);
    }
}
