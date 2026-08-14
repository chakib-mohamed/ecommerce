package the.chak.ecommerce.payment.control.events;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class CapturePaymentCommandDeserializer extends JsonbDeserializer<CapturePaymentCommand> {
    public CapturePaymentCommandDeserializer() {
        super(CapturePaymentCommand.class);
    }
}
