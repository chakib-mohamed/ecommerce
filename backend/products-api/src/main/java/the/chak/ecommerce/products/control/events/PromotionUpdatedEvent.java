package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import the.chak.ecommerce.products.boundary.dto.ProductDto;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PromotionUpdatedEvent {

    ProductDto product;

}
