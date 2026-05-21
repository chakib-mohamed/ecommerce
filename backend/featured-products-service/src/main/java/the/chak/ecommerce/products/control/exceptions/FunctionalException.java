package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public abstract class FunctionalException extends RuntimeException {

    private final Response.Status status;

    protected FunctionalException(String message, Response.Status status) {
        super(message);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }
}
