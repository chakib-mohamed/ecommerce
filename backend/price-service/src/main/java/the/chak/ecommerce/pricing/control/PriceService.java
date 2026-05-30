package the.chak.ecommerce.pricing.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;
import the.chak.ecommerce.pricing.entity.Price;
import the.chak.ecommerce.pricing.repository.PriceRepository;

@ApplicationScoped
public class PriceService {

    private static final Logger LOG = Logger.getLogger(PriceService.class);

    @Inject
    KafkaPriceEventPublisher publisher;

    @Inject
    PriceRepository priceRepository;

    public Price update(String productId, Double price) {
        if (price == null || price <= 0) {
            throw new InvalidPriceException();
        }

        Price entity = priceRepository.find("productId", productId).firstResult();
        if (entity == null) {
            entity = new Price();
            entity.productId = productId;
        }
        entity.price = price;
        priceRepository.persistOrUpdate(entity);
        LOG.infof("Price updated productId=%s newPrice=%.2f", productId, price);

        publisher.publish(new PriceChangedEvent(productId, price));
        return entity;
    }
}
