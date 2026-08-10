package the.chak.ecommerce.payment.control;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What this service puts on the wire, and what it makes of what comes back.
 *
 * <p>Programmed responses rather than {@code stripe-mock}, deliberately. stripe-mock validates the
 * shape of a request against Stripe's published spec, which is worth having and is what the
 * integration test uses - but it is stateless: it does not decline cards, does not honour
 * idempotency keys, and does not time out. Every case below is one of those, so none of them can be
 * produced there.
 */
@Tag("integration")
class StripeGatewayClientTest {

    private static final String STEP_ID = "step-1";
    private static final String PAYMENT_METHOD = "pm_card_visa";

    private WireMockServer stripe;

    @BeforeEach
    void startStripe() {
        stripe = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        stripe.start();
    }

    @AfterEach
    void stopStripe() {
        stripe.stop();
    }

    private StripeGatewayClient client() {
        StripeGatewayClient gateway = new StripeGatewayClient();
        gateway.url = "http://localhost:" + stripe.port();
        gateway.apiKey = "sk_test_dummy";
        gateway.timeout = java.time.Duration.ofSeconds(2);
        return gateway;
    }

    @Test
    @DisplayName("Sends the amount in minor units")
    void charge_sendsMinorUnits() {
        // given
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(okIntent("pi_123", "succeeded")));

        // when
        client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD);

        // then - sent as 10.50 the buyer would be charged ten and a half cents
        stripe.verify(postRequestedFor(urlPathEqualTo("/v1/payment_intents"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .containing("amount=1050")));
    }

    @Test
    @DisplayName("Sends the step id as the idempotency key")
    void charge_sendsTheIdempotencyKey() {
        // given
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(okIntent("pi_123", "succeeded")));

        // when
        client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD);

        // then - without it, a redelivered command is a second charge
        stripe.verify(postRequestedFor(urlPathEqualTo("/v1/payment_intents"))
                .withHeader("Idempotency-Key", equalTo(STEP_ID)));
    }

    @Test
    @DisplayName("Reports a successful intent as captured")
    void charge_succeeded_isCaptured() {
        // given
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(okIntent("pi_123", "succeeded")));

        // when
        ChargeResult result = client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD);

        // then
        assertTrue(result.captured());
        assertEquals("pi_123", result.providerRef());
    }

    @Test
    @DisplayName("Reports a declined card as a refusal, not an error")
    void charge_declined_isARefusal() {
        // given - Stripe answers a decline with 402 and a decline code
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"card_declined\","
                                + "\"decline_code\":\"insufficient_funds\"}}")));

        // when
        ChargeResult result = client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD);

        // then - the provider answered; treating that as a fault would retry a card that has
        // already said no, and would never tell the buyer why
        assertFalse(result.captured());
        assertEquals("insufficient_funds", result.failureReason());
    }

    @Test
    @DisplayName("Treats a provider error as a fault, not a refusal")
    void charge_providerError_isAFault() {
        // given
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(500)));

        // when / then - reported as a decline, the order would cancel over what may be a charge
        // that went through
        assertThrows(PaymentGatewayException.class,
                () -> client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD));
    }

    @Test
    @DisplayName("Treats a provider that stops answering as a fault")
    void charge_timeout_isAFault() {
        // given - the worst case in the spec: the charge may or may not have been made
        stripe.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(okIntent("pi_123", "succeeded").withFixedDelay(5000)));

        // when / then
        assertThrows(PaymentGatewayException.class,
                () -> client().charge(STEP_ID, new BigDecimal("10.50"), "EUR", PAYMENT_METHOD));
    }
    // -- helpers ------------------------------------------------------------

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okIntent(
            String id, String status) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + id + "\",\"status\":\"" + status + "\"}");
    }
}
