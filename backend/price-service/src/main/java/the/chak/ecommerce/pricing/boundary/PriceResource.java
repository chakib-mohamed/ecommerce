package the.chak.ecommerce.pricing.boundary;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.pricing.boundary.dto.PriceResponse;
import the.chak.ecommerce.pricing.boundary.dto.UpdatePriceRequest;
import the.chak.ecommerce.pricing.control.PriceService;
import the.chak.ecommerce.pricing.entity.Price;

@Authenticated
@Path("/prices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PriceResource {

    @Inject
    PriceService priceService;

    @PUT
    @Path("/{productId}")
    public Response updatePrice(@PathParam("productId") String productId, @Valid UpdatePriceRequest request) {
        Price saved = priceService.update(productId, request.getPrice());
        return Response.ok(new PriceResponse(productId, saved.price)).build();
    }
}