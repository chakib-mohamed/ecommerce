package the.chak.ecommerce.products;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import the.chak.ecommerce.products.control.PriceChangedConsumer;
import the.chak.ecommerce.products.control.events.PriceChangedEvent;
import the.chak.ecommerce.products.entity.Product;

@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
public class PriceChangedConsumerTest {

    @Inject
    PriceChangedConsumer consumer;

    @Test
    @Transactional
    public void consume_updatesProductPriceInDatabase() {
        Product product = new Product();
        product.setTitle("Test Product");
        product.setDescription("desc");
        product.setPrice(100.0);
        product.persist();

        String productId = product.getUuid().toString();

        consumer.consume(new PriceChangedEvent(productId, 75.0));

        Product updated = Product.<Product>find("uuid", product.getUuid()).firstResult();
        Assertions.assertEquals(75.0, updated.getPrice(), 0.001);
    }
}
