package the.chak.ecommerce.products.boundary.mapper;

import org.bson.Document;
import org.mapstruct.Mapper;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@Mapper(componentModel = "jakarta")
public interface ProductMapper {

    @org.mapstruct.Mapping(source = "productID", target = "productId")
    @org.mapstruct.Mapping(source = "image", target = "imageKey")
    ProductMongoEntityDto toDto(ProductMongoEntity productMongoEntity);

    default String fromObjectID(org.bson.types.ObjectId objectID) {
        return objectID != null ? objectID.toString() : null;
    }

    default String fromUUID(java.util.UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    default CategoryDto toCategoryDto(Document document) {
        if (document == null) {
            return null;
        }
        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setLabel(document.getString("label"));
        return categoryDto;
    }

    default PromotionDto toPromotionDto(Document document) {
        if (document == null) {
            return null;
        }
        PromotionDto promotionDto = new PromotionDto();
        promotionDto.setLabel(document.getString("label"));
        promotionDto.setActiveFrom(document.get("activeFrom", java.time.LocalDate.class));
        promotionDto.setActiveTo(document.get("activeTo", java.time.LocalDate.class));
        promotionDto.setPercentageOff(document.getDouble("percentageOff"));
        return promotionDto;
    }

}
