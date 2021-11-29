package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.command.CreatePromotionCommand;
import chakmed.ecommerce.products.entity.PromotionDTO;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/promotions")
public interface PromotionsApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PromotionDTO> getPromotions();

    @POST
    public Response createPromotion(CreatePromotionCommand createPromotionCommand);

    @DELETE
    @Path("/{promotionID}")
    public Response deleteProduct(@PathParam("promotionID") String id);




}