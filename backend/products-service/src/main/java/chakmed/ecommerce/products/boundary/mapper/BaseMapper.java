package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductLiteDTO;
import chakmed.ecommerce.products.entity.PromotionDTO;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Mapper
public interface BaseMapper {
    default ObjectId orderIDtoObjectID(String id) {
        return id != null ? new ObjectId(id) : null;
    }

    default String objectIDToString(ObjectId objectID) {
        return objectID != null ? objectID.toString() : null;
    }

    default PromotionDTO documentToPromotionDto(Document promotionDocument) {

        return Optional.ofNullable(promotionDocument).map(
                p -> {
                    PromotionDTO promotionDTO = new PromotionDTO();
                    promotionDTO.setLabel(p.getString("label"));
                    promotionDTO.setActiveFrom(LocalDate.from(p.getDate("activeFrom").toInstant().atZone(ZoneId.systemDefault())));
                    promotionDTO.setActiveTo(LocalDate.from(p.getDate("activeTo").toInstant().atZone(ZoneId.systemDefault())));
                    promotionDTO.setPercentageOff(p.getDouble("percentageOff"));

                    return promotionDTO;
                }
        ).orElse(null);

    }

    ProductLiteDTO mapProductToProductLiteDto(Product product);


}