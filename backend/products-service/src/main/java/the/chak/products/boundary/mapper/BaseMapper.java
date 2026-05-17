package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.boundary.dto.PromotionDto;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Mapper
public interface BaseMapper {
    default ObjectId toObjectID(String id) {
        return id != null ? new ObjectId(id) : null;
    }

    default String fromObjectID(ObjectId objectID) {
        return objectID != null ? objectID.toString() : null;
    }

    default PromotionDto documentToPromotionDto(Document promotionDocument) {

        return Optional.ofNullable(promotionDocument).map(
                p -> {
                    PromotionDto promotionDTO = new PromotionDto();
                    promotionDTO.setLabel(p.getString("label"));
                    promotionDTO.setActiveFrom(LocalDate.from(p.getDate("activeFrom").toInstant().atZone(ZoneId.systemDefault())));
                    promotionDTO.setActiveTo(LocalDate.from(p.getDate("activeTo").toInstant().atZone(ZoneId.systemDefault())));
                    promotionDTO.setPercentageOff(p.getDouble("percentageOff"));

                    return promotionDTO;
                }
        ).orElse(null);

    }

}