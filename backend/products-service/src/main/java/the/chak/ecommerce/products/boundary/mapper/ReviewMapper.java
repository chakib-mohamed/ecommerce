package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.ReviewDto;
import the.chak.ecommerce.products.entity.Review;

@Mapper(componentModel = "jakarta")
public interface ReviewMapper {

    @Mapping(target = "id", source = "uuid")
    @Mapping(target = "productId", source = "productId")
    ReviewDto toDto(Review review);
}
