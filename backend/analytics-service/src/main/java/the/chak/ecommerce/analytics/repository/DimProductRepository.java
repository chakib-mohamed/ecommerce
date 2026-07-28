package the.chak.ecommerce.analytics.repository;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import the.chak.ecommerce.analytics.entity.DimProduct;

/** Product dimension store, keyed on the product uuid. */
@ApplicationScoped
public class DimProductRepository implements PanacheRepositoryBase<DimProduct, String> {
}
