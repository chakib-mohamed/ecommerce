package chakmed.ecommerce.products.boundary;

import lombok.extern.slf4j.Slf4j;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Slf4j
@Provider
public class ExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        log.error("", exception);
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}
