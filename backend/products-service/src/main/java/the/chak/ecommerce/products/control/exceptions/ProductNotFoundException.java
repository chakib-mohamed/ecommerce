package the.chak.ecommerce.products.control.exceptions;

import jakarta.ws.rs.core.Response;
import java.util.UUID;

public class ProductNotFoundException extends FunctionalException {

    public ProductNotFoundException(UUID uuid) {
        super(Response.Status.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found: " + uuid);
    }
}
