package chakmed.ecommerce.orders.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Tuple <X, Y> {

    private final X x;
    private final Y y;
}
