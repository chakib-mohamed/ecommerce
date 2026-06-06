package the.chak.ecommerce.products.control;

import org.mapstruct.Mapper;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.entity.Product;

/**
 * Maps a persisted {@link Product} to the {@link ProductDto} carried in the product-updated outbox
 * payload. Lives in control (not boundary) so the write-path can build the payload inside its own
 * transaction without depending on boundary infrastructure - only {@code boundary.dto} is allowed
 * from here.
 */
@Mapper(componentModel = "jakarta")
public interface ProductEventMapper {

    ProductDto toDto(Product product);
}
