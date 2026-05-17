package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.boundary.dto.ProductDto;
import chakmed.ecommerce.products.boundary.dto.ProductLiteDto;
import chakmed.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import chakmed.ecommerce.products.entity.*;
import org.bson.Document;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface ProductMapper extends BaseMapper {

    Product toEntity(ProductDto productDto);

    ProductDto toDto(Product product);

    ProductMongoEntityDto toDto(ProductMongoEntity productMongoEntity);

    ProductLiteDto mapProductToProductLiteDto(Product product);

}