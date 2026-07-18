package the.chak.ecommerce.products.boundary.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.ProductLiteDto;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.entity.Product;

@Mapper(componentModel = "jakarta")
public interface ProductMapper extends BaseMapper {

    @Mapping(target = "categoryId", expression = "java(deriveCategoryId(product))")
    @Mapping(target = "subcategoryId", expression = "java(deriveSubcategoryId(product))")
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

    /**
     * Top-level category id for the product: the parent of its leaf category, or the leaf itself
     * when that leaf is already top-level.
     */
    default Long deriveCategoryId(Product product) {
        Category leaf = primaryCategory(product);
        if (leaf == null) {
            return null;
        }
        return leaf.getParent() != null ? leaf.getParent().getId() : leaf.getId();
    }

    /**
     * Subcategory id for the product: the leaf category when it has a parent, otherwise omitted
     * (the product is filed directly under a top-level category).
     */
    default Long deriveSubcategoryId(Product product) {
        Category leaf = primaryCategory(product);
        if (leaf == null || leaf.getParent() == null) {
            return null;
        }
        return leaf.getId();
    }

    default Category primaryCategory(Product product) {
        List<Category> categories = product.getCategories();
        return (categories == null || categories.isEmpty()) ? null : categories.get(0);
    }
}
