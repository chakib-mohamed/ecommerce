package the.chak.ecommerce.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import the.chak.ecommerce.products.control.PriceChangedConsumer;
import the.chak.ecommerce.products.control.events.PriceChangedEvent;
import the.chak.ecommerce.products.entity.Product;
import the.chak.ecommerce.products.repository.ProductRepository;

@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class PriceChangedConsumerTest {

    @Inject
    PriceChangedConsumer consumer;

    @Inject
    ProductRepository productRepository;

    @Test
    @DisplayName("Updates the product's stored price when a price-changed event is consumed")
    @Transactional
    void consume_priceChangedEvent_updatesProductPriceInDatabase() {
        // given
        Product product = new Product();
        product.setTitle("Test Product");
        product.setDescription("desc");
        product.setPrice(100.0);
        productRepository.persist(product);
        String productId = product.getUuid().toString();

        // when
        consumer.consume(new PriceChangedEvent(productId, 75.0));

        // then
        Product updated = productRepository.<Product>find("uuid", product.getUuid()).firstResult();
        assertEquals(75.0, updated.getPrice(), 0.001);
    }
}
