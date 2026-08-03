package the.chak.ecommerce.orders.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class StockRejectedEventDeserializer extends JsonbDeserializer<StockRejectedEvent> {
    public StockRejectedEventDeserializer() {
        super(StockRejectedEvent.class);
    }
}
