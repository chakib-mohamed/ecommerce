package the.chak.ecommerce.products.control;

import java.util.List;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.entity.Product;

/**
 * Derives a product's category and subcategory ids from the single category it is filed under.
 *
 * <p>{@code ProductDto} carries both ids, but neither exists on {@code Product} -- they are computed
 * from the category tree. Every mapper that fills that DTO has to apply the same derivation, so it
 * lives here rather than on one of them: the REST mapper had it and the event mapper did not, which
 * left the product event carrying no category at all while the API served one from the same class.
 *
 * <p>Reads {@code Category.parent}, which is lazy, so callers must be inside the transaction that
 * loaded the product.
 */
public final class ProductCategoryDerivation {

    private ProductCategoryDerivation() {
    }

    /**
     * Top-level category id for the product: the parent of its leaf category, or the leaf itself
     * when that leaf is already top-level.
     */
    public static Long categoryId(Product product) {
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
    public static Long subcategoryId(Product product) {
        Category leaf = primaryCategory(product);
        if (leaf == null || leaf.getParent() == null) {
            return null;
        }
        return leaf.getId();
    }

    /** The one category a product is filed under; products carry at most one. */
    public static Category primaryCategory(Product product) {
        List<Category> categories = product.getCategories();
        return (categories == null || categories.isEmpty()) ? null : categories.get(0);
    }
}
