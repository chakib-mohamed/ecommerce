package the.chak.ecommerce.authentication.boundary;

import org.jboss.logging.Logger;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import the.chak.ecommerce.authentication.boundary.dto.ErrorResponse;
import the.chak.ecommerce.authentication.control.exceptions.FunctionalException;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof FunctionalException fe) {
            return Response.status(fe.getStatus())
                    .entity(new ErrorResponse("FUNCTIONAL", fe.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        LOG.error("Unexpected error", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("TECHNICAL", "An unexpected error occurred"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
