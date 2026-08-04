package the.chak.ecommerce.orders.boundary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * What a buyer sends to confirm an order.
 *
 * <p>Carries only a reference to the payment method, never the card itself: the card is exchanged
 * for this reference in the browser, directly with the payment provider, so no card number, expiry
 * or verification value ever reaches this API. The reference is opaque to the platform - passed on
 * as given, never parsed, and never published on any event.
 */
@Data
public class ConfirmOrderRequest {

    @NotBlank
    private String paymentMethod;
}
