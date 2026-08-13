package the.chak.ecommerce.orders.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.ws.rs.POST;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fulfilment endpoints exist on the shared interface, and the contract says the same thing.
 *
 * <p>{@link OrdersApi} is not only this service's JAX-RS interface - it is also the REST client
 * other services bind to, so a method missing here is missing from every caller. And the published
 * {@code openapi.yaml} is what consumers read. The two drifting apart is not hypothetical in this
 * repository: the root and {@code META-INF} copies of this very file documented different fields
 * until that was closed by hand.
 *
 * <p>Plain JUnit on purpose - no Quarkus, no containers - so this stays runnable anywhere and fails
 * for exactly one reason.
 */
class OrdersApiFulfilmentContractTest {

    private static final Path ROOT_SPEC = Path.of("openapi.yaml");
    private static final Path SERVED_SPEC = Path.of("src/main/resources/META-INF/openapi.yaml");

    private Method methodNamed(String name) {
        for (Method method : OrdersApi.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Declares shipOrder, so every caller of the shared interface has it too")
    void ordersApi_declaresShipOrder() {
        Method ship = methodNamed("shipOrder");

        assertNotNull(ship, "OrdersApi should declare shipOrder");
        assertNotNull(ship.getAnnotation(POST.class), "shipOrder should be a POST");
        assertEquals(1, ship.getParameterCount(), "shipOrder takes the order id and nothing else");
    }

    @Test
    @DisplayName("Declares deliverOrder alongside it")
    void ordersApi_declaresDeliverOrder() {
        Method deliver = methodNamed("deliverOrder");

        assertNotNull(deliver, "OrdersApi should declare deliverOrder");
        assertNotNull(deliver.getAnnotation(POST.class), "deliverOrder should be a POST");
        assertEquals(1, deliver.getParameterCount(),
                "deliverOrder takes the order id and nothing else");
    }

    @Test
    @DisplayName("Publishes both endpoints in the contract consumers actually read")
    void openApi_documentsBothEndpoints() throws Exception {
        String spec = Files.readString(ROOT_SPEC);

        assertTrue(spec.contains("/orders/{orderID}/ship:"), "openapi.yaml should document ship");
        assertTrue(spec.contains("/orders/{orderID}/deliver:"),
                "openapi.yaml should document deliver");
    }

    @Test
    @DisplayName("Serves the same contract it publishes")
    void openApi_bothCopiesAgree() throws Exception {
        // The served copy is the one the running service exposes; the root copy is the one people
        // read in the repository. A difference between them is a lie in whichever one you trust.
        assertEquals(Files.readString(ROOT_SPEC), Files.readString(SERVED_SPEC),
                "the root and META-INF copies of openapi.yaml must be identical");
    }
}
