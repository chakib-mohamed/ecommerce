package the.chak.ecommerce.products.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;

/**
 * Verifies the dev/test-only default-image seeding end of things that the test harness can
 * observe: under the {@code test} profile (where the initializer is registered, mirroring
 * dev), the startup initializer uploads each committed placeholder JPEG to object storage
 * under its deterministic key, so the image endpoint serves it.
 *
 * <p>The matching {@code image_key} assignment on seeded product rows is covered by
 * {@code SeedImageAssignerTest}: the Liquibase dev-seed rows (004) are not loaded under the
 * {@code test} profile, which builds its schema from entities, so there is no seeded row to
 * assert against here.
 */
@QuarkusTest
@QuarkusTestResource(StorageTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@Tag("integration")
class SeedProductImagesTest {

    // Deterministic key for the first seeded product (Marble Dining Table) in 004-rich-dev-data.sql.
    private static final String EXPECTED_IMAGE_KEY = "seed-marble-dining-table";

    @Test
    @DisplayName("Serves the default image bytes uploaded at startup for a seed image key")
    void seededImageKey_returnsImageBytes() {
        byte[] image = given().when().get("/products/images/{key}", EXPECTED_IMAGE_KEY)
                .then().statusCode(200)
                .contentType("image/jpeg")
                .extract().asByteArray();

        assertNotNull(image);
        assertTrue(image.length > 0);
    }
}
