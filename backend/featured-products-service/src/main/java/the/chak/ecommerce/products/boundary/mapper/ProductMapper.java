package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import the.chak.ecommerce.products.entity.EmbeddedCategory;
import the.chak.ecommerce.products.entity.EmbeddedPromotion;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@Mapper(componentModel = "cdi")
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

    CategoryDto toCategoryDto(EmbeddedCategory embedded);

    PromotionDto toPromotionDto(EmbeddedPromotion embedded);
}
