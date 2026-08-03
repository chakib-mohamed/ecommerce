package the.chak.ecommerce.orders.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class StockReservedEventDeserializer extends JsonbDeserializer<StockReservedEvent> {
    public StockReservedEventDeserializer() {
        super(StockReservedEvent.class);
    }
}
