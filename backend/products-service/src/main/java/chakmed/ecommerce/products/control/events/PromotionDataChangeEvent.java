package chakmed.ecommerce.products.control.events;

import chakmed.ecommerce.products.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class PromotionDataChangeEvent {

    Product product;

}
