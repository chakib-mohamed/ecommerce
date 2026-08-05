package the.chak.ecommerce.outbox.contract;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyNamingStrategy;

/**
 * The saga's messages as they appear on the wire, shared by the services at both ends of them.
 *
 * <p>Every saga message exists twice in the codebase - once in the service that sends it and once
 * in the service that receives it - because no module may depend on another service's internals.
 * Nothing makes those two copies agree. Rename a field on one side and the code still compiles, the
 * message still publishes, and JSON-B quietly leaves the field null at the other end: a reply whose
 * step id is null is discarded by the orchestrator's guard, so every saga stalls to its deadline
 * and every order is cancelled for a timeout that never happened.
 *
 * <p>These fixtures are the contract those copies are held to. They live here because
 * {@code outbox-common} is the one module both ends already depend on, and they are published as a
 * test-jar so they stay off every runtime classpath.
 *
 * <p>The check each side makes is that a fixture <b>survives a round-trip through its own class
 * unchanged</b>. That single assertion covers renames (the field drops out, since nulls are
 * omitted), additions, removals and retypings, and it covers them in whichever service drifted.
 */
public final class EventContracts {

    private EventContracts() {
    }

    /** JSON-B configured exactly as the services configure it, so fixtures are read as published. */
    public static Jsonb wireJsonb() {
        return JsonbBuilder.create(new JsonbConfig()
                .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES)
                .withNullValues(false));
    }

    /**
     * Loads a canonical payload by topic name, e.g. {@code "reserve-stock"}.
     *
     * @throws IllegalArgumentException if no such fixture exists, rather than returning null and
     *         failing later as an unhelpful NullPointerException
     */
    public static String fixture(String topic) {
        String path = "contracts/" + topic + ".json";
        try (InputStream in = EventContracts.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("No contract fixture on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read contract fixture: " + path, e);
        }
    }

    /**
     * Parses to a structural form so comparisons ignore key order and whitespace.
     *
     * <p>{@code JsonObject} equality is by content, which is what makes the round-trip assertion
     * meaningful: it compares the message, not its formatting.
     */
    public static JsonObject parse(String json) {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }
}
