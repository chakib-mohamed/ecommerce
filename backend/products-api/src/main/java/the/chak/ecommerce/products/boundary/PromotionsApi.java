package the.chak.ecommerce.products.boundary;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.boundary.dto.SavePromotionDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/promotions")
public interface PromotionsApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<PromotionDto> getPromotions();

    @POST
    Response createPromotion(@Valid SavePromotionDto savePromotionDto);

    @DELETE
    @Path("/{promotionID}")
    Response deletePromotion(@PathParam("promotionID") String id);




}