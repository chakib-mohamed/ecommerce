package the.chak.ecommerce.pricing.control.exceptions;

import jakarta.ws.rs.core.Response;

public class InvalidOrderException extends FunctionalException {

    public InvalidOrderException() {
        super(Response.Status.BAD_REQUEST, "INVALID_ORDER", "Order must contain at least one product");
    }
}
