package the.chak.ecommerce.products.control.events;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDeletedEvent {

    UUID productUuid;

}
