package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.MongoDbTestResource;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.control.events.ProductDeletedEvent;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
class KafkaEventConsumerTest {

    @Inject
    KafkaEventConsumer kafkaEventConsumer;

    @BeforeEach
    void setup() {
        ProductMongoEntity.deleteAll();
    }

    @Test
    void consumeProductUpdatedEvent_validPayload_persistsProduct() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto productDto = new ProductDto();
        productDto.setUuid(uuid);
        productDto.setDescription("Test Kafka Product");
        productDto.setImageKey("test-image-key");
        productDto.setPrice(100.0);
        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(1L);
        categoryDto.setLabel("Electronics");
        productDto.setCategories(List.of(categoryDto));
        productDto.setPromotions(List.of());

        // when
        kafkaEventConsumer.consumeProductUpdated(new ProductUpdatedEvent(productDto));

        // then
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertNotNull(entity);
        assertEquals("test-image-key", entity.getImage());
        assertNotNull(entity.getCategories());
        assertEquals(1, entity.getCategories().size());
        assertEquals("Electronics", entity.getCategories().get(0).getLabel());
    }

    @Test
    void consumeProductDeletedEvent_existingProduct_removesFromDatabase() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductMongoEntity entity = new ProductMongoEntity();
        entity.setProductID(uuid);
        entity.setDescription("Delete Me");
        entity.persist();

        // when
        kafkaEventConsumer.consumeProductDeleted(new ProductDeletedEvent(uuid));

        // then
        assertNull(ProductMongoEntity.findByUuid(uuid));
    }
}
