package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class CategoryHasChildrenException extends FunctionalException {

    public CategoryHasChildrenException(Long categoryId) {
        super(Response.Status.CONFLICT, "CATEGORY_HAS_CHILDREN",
                "Category still has subcategories and cannot be deleted: " + categoryId);
    }
}
