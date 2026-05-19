package the.chak.ecommerce.pricing.control;

import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;

@ApplicationScoped
public class KafkaPriceEventPublisher {

    @Inject
    @Channel("price-changed")
    Emitter<PriceChangedEvent> emitter;

    public void publish(PriceChangedEvent event) {
        emitter.send(event);
    }
}
