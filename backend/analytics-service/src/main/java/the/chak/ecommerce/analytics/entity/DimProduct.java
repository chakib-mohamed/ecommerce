package the.chak.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Product dimension: the catalog attributes a sale is sliced by.
 *
 * <p>Maintained from the product event stream, keyed on the product uuid -- the same identifier
 * order lines carry, which is what makes the fact-to-dimension join possible.
 */
@Entity
@Table(name = "dim_product")
@Getter
@Setter
public class DimProduct {

    /** Product uuid, as it appears on order lines. */
    @Id
    @Column(name = "product_id", nullable = false)
    private String productId;

    private String title;

    private Long categoryId;

    private String categoryLabel;

    private Long subcategoryId;

    private String subcategoryLabel;

    /**
     * Set when the product is deleted from the catalog. The row is kept rather than removed so
     * historical sales of a discontinued product keep their category.
     */
    @Column(nullable = false)
    private boolean deleted;
}
