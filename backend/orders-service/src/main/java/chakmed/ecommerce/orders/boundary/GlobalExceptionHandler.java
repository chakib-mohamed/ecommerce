package chakmed.ecommerce.orders.boundary;

import chakmed.ecommerce.orders.boundary.response.ErrorResponse;
import chakmed.ecommerce.orders.control.FunctionalException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public Response handleFunctional(FunctionalException e) {
        return Response.status(e.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(e.getErrorCode(), e.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response handleTechnical(Exception e) {
        Log.errorf(e, "Unexpected technical error: %s", e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"))
                .build();
    }
}
