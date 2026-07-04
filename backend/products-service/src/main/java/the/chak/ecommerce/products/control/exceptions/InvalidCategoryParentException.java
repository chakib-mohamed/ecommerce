package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class InvalidCategoryParentException extends FunctionalException {

    public InvalidCategoryParentException(Long categoryId) {
        super(Response.Status.BAD_REQUEST, "INVALID_CATEGORY_PARENT",
                "A category cannot be its own parent: " + categoryId);
    }
}
