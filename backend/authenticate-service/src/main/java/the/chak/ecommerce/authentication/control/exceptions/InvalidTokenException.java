package the.chak.ecommerce.authentication.control.exceptions;

import jakarta.ws.rs.core.Response;

public class InvalidTokenException extends FunctionalException {

    public InvalidTokenException() {
        super(Response.Status.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or missing authentication token");
    }
}
