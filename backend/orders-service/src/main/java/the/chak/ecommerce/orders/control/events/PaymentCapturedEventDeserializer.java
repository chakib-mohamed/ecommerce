package the.chak.ecommerce.orders.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class PaymentCapturedEventDeserializer extends JsonbDeserializer<PaymentCapturedEvent> {
    public PaymentCapturedEventDeserializer() {
        super(PaymentCapturedEvent.class);
    }
}
