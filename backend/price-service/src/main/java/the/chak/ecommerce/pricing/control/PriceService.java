package the.chak.ecommerce.pricing.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;
import the.chak.ecommerce.pricing.entity.Price;

@ApplicationScoped
public class PriceService {

    @Inject
    KafkaPriceEventPublisher publisher;

    public Price update(String productId, Double price) {
        if (price == null || price <= 0) {
            throw new InvalidPriceException();
        }

        Price entity = Price.<Price>find("productId", productId).firstResult();
        if (entity == null) {
            entity = new Price();
            entity.productId = productId;
        }
        entity.price = price;
        entity.persistOrUpdate();

        publisher.publish(new PriceChangedEvent(productId, price));
        return entity;
    }
}
