package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.boundary.mapper.ProductMapperImpl;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.entity.Product;

/**
 * The event payload and the API response are the same DTO, filled by two different mappers. Only
 * the REST one used to derive the category ids, so the event carried none and every consumer that
 * read them saw null. These tests hold the two to the same output.
 */
class ProductEventMapperTest {

    private final ProductEventMapper eventMapper = new ProductEventMapperImpl();
    private final ProductMapper restMapper = new ProductMapperImpl();

    private static Product productFiledUnder(Category leaf) {
        Product product = new Product();
        product.setTitle("Marble Dining Table");
        product.setPrice(899.99);
        product.setCategories(leaf == null ? List.of() : List.of(leaf));
        return product;
    }

    private static Category category(Long id, String label, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setLabel(label);
        category.setParent(parent);
        return category;
    }

    @Test
    @DisplayName("The event carries the same category ids the API does for a filed product")
    void toDto_productFiledUnderASubcategory_derivesTheSameIdsAsTheRestMapper() {
        // given - filed under a subcategory, so the top-level id is its parent's
        Category dining = category(10L, "Dining", null);
        Product product = productFiledUnder(category(20L, "Dining Tables", dining));

        // when
        ProductDto event = eventMapper.toDto(product);
        ProductDto api = restMapper.toDto(product);

        // then
        assertEquals(10L, event.getCategoryId());
        assertEquals(20L, event.getSubcategoryId());
        assertEquals(api.getCategoryId(), event.getCategoryId());
        assertEquals(api.getSubcategoryId(), event.getSubcategoryId());
    }

    @Test
    @DisplayName("A product filed straight under a top-level category reports no subcategory")
    void toDto_productFiledUnderATopLevelCategory_reportsNoSubcategory() {
        // given
        Product product = productFiledUnder(category(10L, "Dining", null));

        // when
        ProductDto event = eventMapper.toDto(product);

        // then
        assertEquals(10L, event.getCategoryId());
        assertNull(event.getSubcategoryId());
    }

    @Test
    @DisplayName("A product filed under nothing reports no category")
    void toDto_productWithoutCategories_reportsNoCategory() {
        // given
        Product product = productFiledUnder(null);

        // when
        ProductDto event = eventMapper.toDto(product);

        // then
        assertNull(event.getCategoryId());
        assertNull(event.getSubcategoryId());
    }

    @Test
    @DisplayName("The event keeps a product's categories shallow, without walking the tree")
    void toDto_always_keepsCategoriesShallow() {
        // given - a leaf whose parent also lists it as a child
        Category dining = category(10L, "Dining", null);
        Category diningTables = category(20L, "Dining Tables", dining);
        dining.setSubCategories(List.of(diningTables));
        Product product = productFiledUnder(diningTables);

        // when
        ProductDto event = eventMapper.toDto(product);

        // then - the subtree stays out of the payload
        assertEquals(1, event.getCategories().size());
        assertEquals(20L, event.getCategories().get(0).getId());
        assertNull(event.getCategories().get(0).getParentId());
        assertNull(event.getCategories().get(0).getSubCategories());
    }
}
