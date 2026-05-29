package the.chak.ecommerce.products.boundary;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.boundary.dto.SavePromotionDto;
import the.chak.ecommerce.products.boundary.mapper.PromotionMapper;
import the.chak.ecommerce.products.control.PromotionService;

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

        return promotionService.listAll().stream().map(promotionMapper::toDto)
                .collect(Collectors.toList());

    }

    @POST
    public Response createPromotion(SavePromotionDto savePromotionDto) {

        var promotion = promotionMapper.fromDto(savePromotionDto);

        promotionService.savePromotion(promotion);

        return Response.status(Response.Status.CREATED).entity(promotionMapper.toDto(promotion)).build();
    }

    @Override
    public Response deletePromotion(String id) {
        promotionService.deletePromotion(Long.valueOf(id));
        return Response.ok().status(200).build();
    }
}
