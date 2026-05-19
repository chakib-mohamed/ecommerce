package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import the.chak.ecommerce.products.boundary.dto.ProductDto;


@Data
@AllArgsConstructor
public class PromotionUpdatedEvent {

    ProductDto product;

}
