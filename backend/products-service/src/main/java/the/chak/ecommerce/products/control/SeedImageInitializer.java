package the.chak.ecommerce.products.control;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Dev/test-only default-image seeding. On startup, for each locally-seeded product this
 * uploads a committed placeholder JPEG to object storage under a deterministic key and sets
 * the product's {@code image_key} to that key (only when it is still null, so an
 * API-uploaded image is never clobbered).
 *
 * <p>Gated by {@code products.seed-images.enabled} (a runtime flag, on for {@code %dev} and
 * {@code %test}, off by default) so production neither uploads images nor sets {@code
 * image_key}. The upload runs on every startup, which restores objects after the local
 * object store is reset between runs.
 */
@ApplicationScoped
public class SeedImageInitializer {

    private static final Logger LOG = Logger.getLogger(SeedImageInitializer.class);

    private static final String CLASSPATH_DIR = "seed-images/";

    /** Seeded product uuid (from 004-rich-dev-data.sql) to its deterministic image key. */
    private static final Map<UUID, String> SEED_IMAGES = Map.ofEntries(
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000001"), "seed-marble-dining-table"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000002"), "seed-oak-dining-table"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000003"), "seed-velvet-dining-chair"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000004"), "seed-walnut-sideboard"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000005"), "seed-l-shape-corner-sofa"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000006"), "seed-glass-coffee-table"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000007"), "seed-industrial-tv-stand"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000008"), "seed-king-size-platform-bed"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000009"), "seed-solid-oak-nightstand"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000010"), "seed-teak-garden-table"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000011"), "seed-folding-garden-chair"),
            Map.entry(UUID.fromString("a0000000-0000-0000-0000-000000000012"), "seed-rattan-coffee-table"));

    @Inject
    StorageService storageService;

    @Inject
    SeedImageAssigner seedImageAssigner;

    @ConfigProperty(name = "products.seed-images.enabled")
    boolean enabled;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        int uploaded = 0;
        int updated = 0;
        for (Map.Entry<UUID, String> seed : SEED_IMAGES.entrySet()) {
            byte[] bytes = readSeedImage(seed.getValue());
            if (bytes == null) {
                continue;
            }
            storageService.uploadImage(seed.getValue(), bytes);
            uploaded++;
            if (seedImageAssigner.assignImageKey(seed.getKey(), seed.getValue())) {
                updated++;
            }
        }
        LOG.infof("Seed images initialized uploaded=%d rowsUpdated=%d", uploaded, updated);
    }

    private byte[] readSeedImage(String key) {
        String resource = CLASSPATH_DIR + key + ".jpg";
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warnf("Seed image not found on classpath resource=%s", resource);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LOG.warnf(e, "Failed to read seed image resource=%s", resource);
            return null;
        }
    }

}
