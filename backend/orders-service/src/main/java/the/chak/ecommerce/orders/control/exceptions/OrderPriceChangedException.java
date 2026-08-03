package the.chak.ecommerce.orders.control.exceptions;

import java.util.List;
import jakarta.ws.rs.core.Response;

/**
 * Raised when the catalog price of something in the order moved between the order being placed and
 * being confirmed. Reported as 409, and the message names each affected product with its previous
 * and current price so the buyer can be shown what changed rather than a bare failure.
 *
 * <p>An order is priced when it is created and can sit unconfirmed indefinitely, so without this
 * check a buyer would be billed whatever the catalog said at some arbitrary earlier moment.
 */
public class OrderPriceChangedException extends FunctionalException {

    public OrderPriceChangedException(List<String> changes) {
        super(Response.Status.CONFLICT, "ORDER_PRICE_CHANGED",
                "Prices changed since the order was placed: " + String.join("; ", changes));
    }
}
