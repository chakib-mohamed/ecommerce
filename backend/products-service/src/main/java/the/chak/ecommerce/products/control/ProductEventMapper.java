package the.chak.ecommerce.products.control;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.entity.Product;

/**
 * Maps a persisted {@link Product} to the {@link ProductDto} carried in the product-updated outbox
 * payload. Lives in control (not boundary) so the write-path can build the payload inside its own
 * transaction without depending on boundary infrastructure - only {@code boundary.dto} is allowed
 * from here.
 *
 * <p>Fills the same derived category ids the REST mapper does. The two share a DTO, and a consumer
 * cannot tell which mapper produced the copy it received, so the event must not carry less than the
 * API does for the same product.
 */
@Mapper(componentModel = "jakarta", imports = ProductCategoryDerivation.class)
public interface ProductEventMapper {

    @Mapping(target = "categoryId", expression = "java(ProductCategoryDerivation.categoryId(product))")
    @Mapping(target = "subcategoryId",
            expression = "java(ProductCategoryDerivation.subcategoryId(product))")
    ProductDto toDto(Product product);

    /**
     * Shallow, for the same reason the read path is: walking the tree touches associations the write
     * transaction never primed, and would sink a category's whole subtree into every event.
     */
    @Mapping(target = "parentId", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    CategoryDto categoryToDto(Category category);
}
