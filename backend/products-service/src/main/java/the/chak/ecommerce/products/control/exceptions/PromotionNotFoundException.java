package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;

public class PromotionNotFoundException extends FunctionalException {

    public PromotionNotFoundException(Long id) {
        super(Response.Status.NOT_FOUND, "Promotion not found: " + id);
    }
}
