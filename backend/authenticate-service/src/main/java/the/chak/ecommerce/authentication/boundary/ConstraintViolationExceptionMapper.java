package the.chak.ecommerce.authentication.boundary;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import the.chak.ecommerce.authentication.boundary.dto.ErrorResponse;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation error");
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("FUNCTIONAL", "VALIDATION_ERROR", message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
