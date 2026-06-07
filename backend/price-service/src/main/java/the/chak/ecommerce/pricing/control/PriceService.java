package the.chak.ecommerce.pricing.control;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import the.chak.ecommerce.pricing.control.events.PriceChangedEvent;
import the.chak.ecommerce.pricing.control.exceptions.InvalidPriceException;
import the.chak.ecommerce.pricing.entity.OutboxEntry;
import the.chak.ecommerce.pricing.entity.Price;
import the.chak.ecommerce.pricing.repository.OutboxRepository;
import the.chak.ecommerce.pricing.repository.PriceRepository;

/**
 * Owns the price write-path. An update commits the {@link Price} document and a matching
 * {@code price-changed} {@link OutboxEntry} in a single Mongo transaction (replica-set required),
 * so the event can never be lost relative to the business change - the dual-write that previously
 * sent to Kafka outside the persistence boundary is gone. {@link OutboxRelay} drains the entry to
 * the broker; {@link #update} only nudges it awake after the commit.
 */
@ApplicationScoped
public class PriceService {

    private static final Logger LOG = Logger.getLogger(PriceService.class);

    @Inject
    MongoClient mongoClient;

    @Inject
    PriceRepository priceRepository;

    @Inject
    OutboxRepository outboxRepository;

    @Inject
    OutboxEventFactory outboxEventFactory;

    @Inject
    OutboxRelay outboxRelay;

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

        OutboxEntry outboxEntry =
                outboxEventFactory.priceChanged(productId, new PriceChangedEvent(productId, price));

        Price toWrite = entity;
        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                priceRepository.mongoCollection().replaceOne(
                        session,
                        Filters.eq("productId", productId),
                        toWrite,
                        new ReplaceOptions().upsert(true));
                outboxRepository.mongoCollection().insertOne(session, outboxEntry);
                return null;
            });
        }
        LOG.infof("Price updated productId=%s newPrice=%.2f", productId, price);

        // Best-effort wake-up; if it is lost the scheduled tick still drains the entry.
        outboxRelay.requestPoll();
        return entity;
    }
}
