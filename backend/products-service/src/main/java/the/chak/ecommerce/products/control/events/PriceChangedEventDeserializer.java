package the.chak.ecommerce.products.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class PriceChangedEventDeserializer extends JsonbDeserializer<PriceChangedEvent> {
    public PriceChangedEventDeserializer() {
        super(PriceChangedEvent.class);
    }
}
