package the.chak.ecommerce.orders.boundary.dto;

import lombok.Data;

import jakarta.json.bind.annotation.JsonbDateFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An order as it crosses a boundary: the HTTP API and the {@code order-paid} event payload.
 *
 * <p>Carries no card details. Payment integrates through an opaque token reference held elsewhere,
 * so a card number, expiry or verification value never enters the order aggregate and is never
 * persisted or published. See the "Card data" section of {@code docs/specs/order-lifecycle.md}.
 */
@Data
public class OrderDTO {
    private String id;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime creationDate;

    private BigDecimal price;

    /** Denomination of {@code price}. One currency, no conversion. */
    private String currency = Money.DEFAULT_CURRENCY;

    private List<ProductVO> products;
    private String userID;
    private OrderStatus status;

}
