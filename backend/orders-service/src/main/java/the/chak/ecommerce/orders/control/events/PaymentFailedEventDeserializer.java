package the.chak.ecommerce.orders.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class PaymentFailedEventDeserializer extends JsonbDeserializer<PaymentFailedEvent> {
    public PaymentFailedEventDeserializer() {
        super(PaymentFailedEvent.class);
    }
}
