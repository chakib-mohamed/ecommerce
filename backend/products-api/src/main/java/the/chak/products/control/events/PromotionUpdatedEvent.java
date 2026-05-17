package the.chak.products.control.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import the.chak.products.entity.Product;


@Data
@AllArgsConstructor
public class PromotionDataChangeEvent {

    Product product;

}
