package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.ProductLiteDto;
import the.chak.ecommerce.products.entity.Product;

@Mapper(componentModel = "jakarta")
public interface ProductMapper extends BaseMapper {

    ProductDto toDto(Product product);

    Product toEntity(ProductDto productDto);

    ProductLiteDto mapProductToProductLiteDto(Product product);

}
