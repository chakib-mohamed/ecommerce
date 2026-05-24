package the.chak.ecommerce.pricing.control.exceptions;

import jakarta.ws.rs.core.Response;

public class InvalidPriceException extends FunctionalException {

    public InvalidPriceException() {
        super(Response.Status.BAD_REQUEST, "INVALID_PRICE", "Price must be a positive value");
    }
}
