package the.chak.ecommerce.products.boundary;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import the.chak.ecommerce.products.boundary.dto.ErrorCode;
import the.chak.ecommerce.products.boundary.dto.ErrorDto;

@Slf4j
@Provider
public class ExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ConstraintViolationException) {
            log.error("A ConstraintViolationException occurred ", exception);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto(ErrorCode.BAD_REQUEST, exception.getMessage())).build();
        }

        if (exception instanceof WebApplicationException) {
            return ((WebApplicationException) exception).getResponse();
        }

        if (exception instanceof IllegalArgumentException) {
            log.error("An IllegalArgumentException occurred ", exception);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto(ErrorCode.BAD_REQUEST, exception.getMessage())).build();
        }

        log.error("An Exception occurred ", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorDto(ErrorCode.INTERNAL_SERVER_ERROR, "Internal Server Error"))
                .build();
    }
}
