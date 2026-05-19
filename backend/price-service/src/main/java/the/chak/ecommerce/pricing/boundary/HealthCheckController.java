package the.chak.ecommerce.pricing.boundary;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/info")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HealthCheckController {

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("STATUS", "UP")).build();
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(Map.of()).build();
    }

}