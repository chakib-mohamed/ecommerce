package the.chak.ecommerce.products.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * A category as it is stored inside a product document.
 *
 * <p>Accessors only, no generated {@code equals}/{@code hashCode}/{@code toString} - the convention
 * for everything in {@code entity/}. Nothing compares these or puts them in a set; what a generated
 * {@code equals} would buy is unused, and what it costs is a second notion of identity for a value
 * that already has one through the product that owns it.
 */
@Getter
@Setter
public class EmbeddedCategory {
    private Long id;
    private String label;
}
