package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.boundary.command.CreatePromotionCommand;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.Promotion;
import chakmed.ecommerce.products.entity.PromotionDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface PromotionMapper extends BaseMapper {

    default Promotion mapCreatePromotionCommandToPromotion(CreatePromotionCommand createPromotionCommand) {
        var promotion = new Promotion();
        promotion.setLabel(createPromotionCommand.getLabel());
        promotion.setProduct(Product.findById(Long.valueOf(createPromotionCommand.getProductID())));
        promotion.setActiveFrom(createPromotionCommand.getActiveFrom());
        promotion.setActiveTo(createPromotionCommand.getActiveTo());
        promotion.setPercentageOff(createPromotionCommand.getPercentageOff());

        return promotion;
    }

    PromotionDTO promotionToDTO(Promotion p);

}