package the.chak.ecommerce.pricing.boundary;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import the.chak.ecommerce.pricing.boundary.dto.ErrorResponse;
import the.chak.ecommerce.pricing.control.exceptions.FunctionalException;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public Response handleFunctional(FunctionalException ex) {
        return Response.status(ex.getStatus())
                .entity(new ErrorResponse("FUNCTIONAL", ex.getMessage()))
                .build();
    }

    @ServerExceptionMapper
    public Response handleUnexpected(Exception ex) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("TECHNICAL", "An unexpected error occurred"))
                .build();
    }
}
