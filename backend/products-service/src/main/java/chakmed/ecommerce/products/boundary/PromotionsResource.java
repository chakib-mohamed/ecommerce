package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.command.CreatePromotionCommand;
import chakmed.ecommerce.products.boundary.mapper.PromotionMapper;
import chakmed.ecommerce.products.control.PromotionService;
import chakmed.ecommerce.products.entity.Promotion;
import chakmed.ecommerce.products.entity.PromotionDTO;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/promotions")
public class PromotionsResource implements PromotionsApi {

    @Inject
    PromotionService promotionService;

    @Inject
    PromotionMapper promotionMapper;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PromotionDTO> getPromotions() {

        return Promotion.listAll().stream()
                .map(Promotion.class::cast)
                .map(promotionMapper::promotionToDTO)
                .collect(Collectors.toList());

    }

    @POST
    public Response createPromotion(CreatePromotionCommand createPromotionCommand) {

        var promotion = promotionMapper.mapCreatePromotionCommandToPromotion(createPromotionCommand);

        promotionService.savePromotion(promotion);

        return Response.ok(promotionMapper.promotionToDTO(promotion)).status(201).build();
    }

    @DELETE
    @Path("/{promotionID}")
    public Response deleteProduct(@PathParam("promotionID") String id) {

       promotionService.deletePromotion(Long.valueOf(id));

        return Response.ok().status(200).build();
    }




}