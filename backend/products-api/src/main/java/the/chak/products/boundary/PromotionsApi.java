package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.dto.PromotionDto;
import chakmed.ecommerce.products.boundary.dto.SavePromotionDto;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/promotions")
public interface PromotionsApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<PromotionDto> getPromotions();

    @POST
    Response createPromotion(SavePromotionDto savePromotionDto);

    @DELETE
    @Path("/{promotionID}")
    Response deletePromotion(@PathParam("promotionID") String id);




}