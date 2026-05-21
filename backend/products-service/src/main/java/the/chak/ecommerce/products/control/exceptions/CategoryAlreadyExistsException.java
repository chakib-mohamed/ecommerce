package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class CategoryAlreadyExistsException extends FunctionalException {

    public CategoryAlreadyExistsException(String label) {
        super(Response.Status.BAD_REQUEST, "Category already exists: " + label);
    }
}
