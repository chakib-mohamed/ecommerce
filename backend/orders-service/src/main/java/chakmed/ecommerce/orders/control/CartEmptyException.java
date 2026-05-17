package chakmed.ecommerce.orders.control;

import jakarta.ws.rs.core.Response;

public class CartEmptyException extends FunctionalException {

    public CartEmptyException() {
        super(Response.Status.BAD_REQUEST, "CART_EMPTY", "Cart is empty");
    }
}
