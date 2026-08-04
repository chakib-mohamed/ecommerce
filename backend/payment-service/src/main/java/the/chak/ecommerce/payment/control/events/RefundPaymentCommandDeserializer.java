package the.chak.ecommerce.payment.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class RefundPaymentCommandDeserializer extends JsonbDeserializer<RefundPaymentCommand> {
    public RefundPaymentCommandDeserializer() {
        super(RefundPaymentCommand.class);
    }
}
