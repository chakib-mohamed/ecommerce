package the.chak.ecommerce.payment;

import java.util.Map;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * A stand-in payment provider that answers, so a capture can run end to end.
 *
 * <p>WireMock rather than stripe-mock: stripe-mock validates the shape of a request but keeps no
 * state, so it cannot be told to decline, and a test that cannot express "this card was refused"
 * can only ever cover the happy path.
 *
 * <p>The outcome is chosen by the card in the request, not by switching a shared stub between
 * tests. Switching was the first attempt and it made the tests order-dependent: they pass alone and
 * fail together, because the answer is given on a consumer thread some time after the test that
 * set it up has moved on. Matching on the request keeps every case true at once.
 */
public class StripeTestResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    /** The provider reference handed back for a successful charge, so tests can assert it. */
    public static final String PROVIDER_REF = "pi_integration_0001";

    /** Charged successfully. */
    public static final String CARD_OK = "pm_card_visa";

    /** Refused by the provider - an answer, not a fault. */
    public static final String CARD_DECLINED = "pm_card_declined";

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        server.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .withRequestBody(WireMock.containing(CARD_DECLINED))
                .willReturn(WireMock.aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"card_declined\","
                                + "\"decline_code\":\"insufficient_funds\"}}")));

        // Lower priority, so the decline stub above wins when both could match.
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/payment_intents"))
                .atPriority(10)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + PROVIDER_REF + "\",\"status\":\"succeeded\"}")));

        return Map.of("payment.stripe.url", "http://localhost:" + server.port());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
