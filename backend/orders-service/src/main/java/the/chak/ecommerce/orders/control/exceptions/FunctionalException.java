package the.chak.ecommerce.orders.control.exceptions;

import jakarta.ws.rs.core.Response;

public abstract class FunctionalException extends RuntimeException {

    private final Response.Status status;
    private final String errorCode;

    protected FunctionalException(Response.Status status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public Response.Status getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
