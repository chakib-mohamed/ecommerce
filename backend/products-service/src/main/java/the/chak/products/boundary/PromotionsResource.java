package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.dto.PromotionDto;
import chakmed.ecommerce.products.boundary.dto.SavePromotionDto;
import chakmed.ecommerce.products.boundary.mapper.PromotionMapper;
import chakmed.ecommerce.products.control.PromotionService;
import chakmed.ecommerce.products.entity.Promotion;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
    public List<PromotionDto> getPromotions() {

        return Promotion.listAll().stream()
                .map(Promotion.class::cast)
                .map(promotionMapper::toDto)
                .collect(Collectors.toList());

    }

    @POST
    public Response createPromotion(SavePromotionDto savePromotionDto) {

        var promotion = promotionMapper.fromDto(savePromotionDto);

        promotionService.savePromotion(promotion);

        return Response.ok(promotionMapper.toDto(promotion)).status(201).build();
    }

    @Override
    public Response deletePromotion(String id) {
        promotionService.deletePromotion(Long.valueOf(id));
        return Response.ok().build();
    }

    @DELETE
    @Path("/{promotionID}")
    public Response deleteProduct(@PathParam("promotionID") String id) {

       promotionService.deletePromotion(Long.valueOf(id));

        return Response.ok().status(200).build();
    }




}