package the.chak.ecommerce.payment.control;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Talks to Stripe's Payment Intents API. The only thing in the platform that knows the provider's
 * dialect.
 *
 * <p>Which endpoint it talks to is decided by configuration alone - the mock in local and test, the
 * real API elsewhere. There is no code path that chooses between them, and so no way for a
 * misconfiguration to silently fall back to a mock and report charges that never happened.
 */
@ApplicationScoped
public class StripeGatewayClient {

    /** API base URL. The mock locally and in tests, the real API elsewhere - config decides. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "payment.stripe.url")
    String url;

    /** Secret key. Supplied by the environment; never committed, never logged. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "payment.stripe.api-key")
    String apiKey;

    /** Per-request timeout, kept shorter than the saga step deadline. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "payment.stripe.timeout", defaultValue = "PT10S")
    java.time.Duration timeout;

    /**
     * Creates and confirms one intent - one charge.
     *
     * @param idempotencyKey the saga step id, so a redelivered command returns the original charge
     *                       rather than making a second one
     * @param paymentMethod the buyer's single-use payment method reference
     */
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String currency,
            String paymentMethod) {
        String body = "amount=" + toMinorUnits(amount)
                + "&currency=" + enc(currency.toLowerCase(java.util.Locale.ROOT))
                + "&payment_method=" + enc(paymentMethod)
                + "&confirm=true";
        HttpResponse<String> response = send("/v1/payment_intents", idempotencyKey, body);

        int status = response.statusCode();
        if (status == 200) {
            return ChargeResult.captured(field(response.body(), "id"));
        }
        if (status == 402) {
            // The provider answered, and the answer was no. A refusal is an outcome, not a fault:
            // retrying it would re-present a card that has already declined, and reporting it as a
            // fault would lose the reason the buyer needs to be told.
            return ChargeResult.declined(declineReason(response.body()));
        }
        throw new PaymentGatewayException(
                "Payment provider returned " + status + " creating an intent");
    }

    private HttpResponse<String> send(String path, String idempotencyKey, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // The saga step id. This, not our own records, is what stops a redelivered command
                // charging twice - it holds even if this service loses its database entirely.
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            // Includes the timeout. Nobody knows whether money moved, so this must not be
            // mistaken for a refusal: see PaymentService#capture.
            throw new PaymentGatewayException("Payment provider did not answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted awaiting the payment provider", e);
        }
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        }
        return httpClient;
    }

    private volatile HttpClient httpClient;

    /**
     * Converts an amount to the integer minor units Stripe takes: 10.50 becomes 1050.
     *
     * <p>Exact by design. A value carrying more than two decimals is a bug somewhere upstream, and
     * on the way to a real charge it has to throw rather than quietly round to something the buyer
     * did not agree to. {@code stripTrailingZeros} first, so 10.5000 - the same money written
     * longer - converts rather than being refused for a precision it does not actually use.
     */
    static long toMinorUnits(BigDecimal amount) {
        java.util.Objects.requireNonNull(amount, "amount");
        return amount.stripTrailingZeros().movePointRight(2).longValueExact();
    }

    /** Stripe's own decline code where it gave one, its generic code otherwise. */
    private static String declineReason(String body) {
        String declineCode = field(body, "decline_code");
        return declineCode != null ? declineCode : field(body, "code");
    }

    /**
     * Pulls one string field out of a JSON body.
     *
     * <p>Only three scalars are ever read back - an intent id and two error codes - so this stays a
     * regex rather than a bound response type. A shape we do not recognise yields null and is
     * handled by the caller, which is the behaviour we want anyway.
     */
    private static String field(String body, String name) {
        if (body == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
