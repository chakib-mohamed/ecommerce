package the.chak.ecommerce.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductUpdatedEvent {

    ProductDto product;

}
