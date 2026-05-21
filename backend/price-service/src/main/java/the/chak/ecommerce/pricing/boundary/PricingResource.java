package the.chak.ecommerce.pricing.boundary;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationResponse;
import the.chak.ecommerce.pricing.control.PricingService;

@Authenticated
@Path("/pricing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PricingResource {

    @Inject
    PricingService pricingService;

    @POST
    @Path("/calculate")
    public Response calculatePrice(PriceCalculationRequest request) {
        PriceCalculationResponse response = pricingService.calculate(request);
        return Response.ok(response).build();
    }
}
