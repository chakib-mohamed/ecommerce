package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.boundary.dto.SavePromotionDto;
import the.chak.ecommerce.products.entity.Promotion;

@Mapper(componentModel = "jakarta")
public interface PromotionMapper extends BaseMapper {

    default Promotion fromDto(SavePromotionDto savePromotionDto) {
        var promotion = new Promotion();
        promotion.setLabel(savePromotionDto.getLabel());
        promotion.setActiveFrom(savePromotionDto.getActiveFrom());
        promotion.setActiveTo(savePromotionDto.getActiveTo());
        promotion.setPercentageOff(savePromotionDto.getPercentageOff());

        return promotion;
    }

    PromotionDto toDto(Promotion p);

}
