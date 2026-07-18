package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class ParentCategoryNotFoundException extends FunctionalException {

    public ParentCategoryNotFoundException(Long parentId) {
        super(Response.Status.BAD_REQUEST, "PARENT_CATEGORY_NOT_FOUND",
                "Parent category does not exist: " + parentId);
    }
}
