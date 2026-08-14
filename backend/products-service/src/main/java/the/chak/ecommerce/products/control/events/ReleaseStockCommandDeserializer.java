package the.chak.ecommerce.products.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ReleaseStockCommandDeserializer extends JsonbDeserializer<ReleaseStockCommand> {
    public ReleaseStockCommandDeserializer() {
        super(ReleaseStockCommand.class);
    }
}
