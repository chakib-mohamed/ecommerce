package the.chak.ecommerce.products.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ReserveStockCommandDeserializer extends JsonbDeserializer<ReserveStockCommand> {
    public ReserveStockCommandDeserializer() {
        super(ReserveStockCommand.class);
    }
}
