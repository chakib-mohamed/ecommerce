package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.ProductLiteDto;
import the.chak.ecommerce.products.control.ProductCategoryDerivation;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.entity.Product;

@Mapper(componentModel = "jakarta", imports = ProductCategoryDerivation.class)
public interface ProductMapper extends BaseMapper {

    @Mapping(target = "categoryId", expression = "java(ProductCategoryDerivation.categoryId(product))")
    @Mapping(target = "subcategoryId",
            expression = "java(ProductCategoryDerivation.subcategoryId(product))")
    ProductDto toDto(Product product);

    Product toEntity(ProductDto productDto);

    ProductLiteDto mapProductToProductLiteDto(Product product);

    /**
     * Maps a product's own category links shallowly (no parent/children traversal): the read path
     * primes only the product's leaf categories, so navigating the wider tree here would touch
     * uninitialized associations. The hierarchy is conveyed via {@code categoryId}/{@code subcategoryId}.
     */
    @Mapping(target = "parentId", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    CategoryDto categoryToDto(Category category);
}
