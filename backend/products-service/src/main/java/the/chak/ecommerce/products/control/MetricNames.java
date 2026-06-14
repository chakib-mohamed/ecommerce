package the.chak.ecommerce.products.control;

/**
 * Business (functional) metric names and tag keys for products-service. Micrometer dot-notation;
 * the Prometheus registry renders these as {@code catalog_products_mutations_total},
 * {@code catalog_categories_mutations_total}, {@code catalog_promotions_mutations_total},
 * {@code catalog_images_uploaded_total}, and {@code catalog_price_updates_consumed_total}. See
 * {@code docs/specs/functional-metrics.md}.
 */
public final class MetricNames {

    private MetricNames() {
    }

    /** Counter - product writes, tagged by {@link #TAG_OP}. */
    public static final String CATALOG_PRODUCTS_MUTATIONS = "catalog.products.mutations";

    /** Counter - category writes, tagged by {@link #TAG_OP}. */
    public static final String CATALOG_CATEGORIES_MUTATIONS = "catalog.categories.mutations";

    /** Counter - promotion writes, tagged by {@link #TAG_OP}. */
    public static final String CATALOG_PROMOTIONS_MUTATIONS = "catalog.promotions.mutations";

    /** Counter - product images uploaded to storage. */
    public static final String CATALOG_IMAGES_UPLOADED = "catalog.images.uploaded";

    /** Counter - price-changed events consumed and applied to a known product. */
    public static final String CATALOG_PRICE_UPDATES_CONSUMED = "catalog.price.updates.consumed";

    /** Tag key distinguishing the kind of catalog write. */
    public static final String TAG_OP = "op";
    public static final String OP_CREATE = "create";
    public static final String OP_UPDATE = "update";
    public static final String OP_DELETE = "delete";
}
