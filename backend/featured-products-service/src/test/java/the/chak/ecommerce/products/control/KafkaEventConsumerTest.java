package the.chak.ecommerce.products.control;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
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
    @Channel("product-updated-out")
    Emitter<ProductUpdatedEvent> productUpdatedEmitter;

    @Inject
    @Channel("product-deleted-out")
    Emitter<ProductDeletedEvent> productDeletedEmitter;

    @BeforeEach
    void setup() {
        ProductMongoEntity.deleteAll();
    }

    @Test
    void consumeProductUpdatedEvent_validPayload_persistsAndServesViaRest() {
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
        productUpdatedEmitter.send(new ProductUpdatedEvent(productDto));
        await().atMost(20, TimeUnit.SECONDS).until(() -> {
            ProductMongoEntity p = ProductMongoEntity.findByUuid(uuid);
            return p != null && p.getCategories() != null && !p.getCategories().isEmpty();
        });

        // then
        ProductMongoEntity entity = ProductMongoEntity.findByUuid(uuid);
        assertNotNull(entity);
        assertEquals("test-image-key", entity.getImage());
        assertNotNull(entity.getCategories());
        assertEquals(1, entity.getCategories().size());
        assertEquals("Electronics", entity.getCategories().get(0).getLabel());

        given().when().get("/products/featured").then().log().all().statusCode(200)
                .body("find { it.product_id == '" + uuid + "' }.categories", hasSize(1))
                .body("find { it.product_id == '" + uuid + "' }.image_key", is("test-image-key"))
                .body("find { it.product_id == '" + uuid + "' }.categories[0].label", is("Electronics"));
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
        productDeletedEmitter.send(new ProductDeletedEvent(uuid));

        // then
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> ProductMongoEntity.findByUuid(uuid) == null);
    }
}
