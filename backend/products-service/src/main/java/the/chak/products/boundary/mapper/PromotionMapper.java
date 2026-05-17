package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.boundary.dto.PromotionDto;
import chakmed.ecommerce.products.boundary.dto.SavePromotionDto;
import chakmed.ecommerce.products.entity.Promotion;
import org.bson.Document;
import org.mapstruct.Mapper;

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

    default Document toDocument(Promotion promotion) {

        Document promotionDocument = new Document();
        promotionDocument.put("label", promotion.getLabel());
        promotionDocument.put("activeFrom", promotion.getActiveFrom());
        promotionDocument.put("activeTo", promotion.getActiveTo());
        promotionDocument.put("percentageOff", promotion.getPercentageOff());
        return promotionDocument;
    }

    PromotionDto toDto(Promotion p);

}