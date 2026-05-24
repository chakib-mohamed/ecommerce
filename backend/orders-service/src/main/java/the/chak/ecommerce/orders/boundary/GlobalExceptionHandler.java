package the.chak.ecommerce.orders.boundary;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.dto.ErrorResponse;
import the.chak.ecommerce.orders.control.exceptions.FunctionalException;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof FunctionalException fe) {
            return Response.status(fe.getStatus())
                    .entity(new ErrorResponse("FUNCTIONAL", fe.getErrorCode(), fe.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        LOG.error("Unexpected error", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("TECHNICAL", "INTERNAL_ERROR", "An unexpected error occurred"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
