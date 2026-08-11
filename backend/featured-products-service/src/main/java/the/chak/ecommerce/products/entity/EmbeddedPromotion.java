package the.chak.ecommerce.products.entity;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A promotion as it is stored inside a product document.
 *
 * <p>Accessors only, for the same reason as {@link EmbeddedCategory}: {@code entity/} holds no
 * generated identity, and nothing here relies on one.
 */
@Getter
@Setter
public class EmbeddedPromotion {
    private String label;
    private LocalDate activeFrom;
    private LocalDate activeTo;
    private Double percentageOff;
}
