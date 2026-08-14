package the.chak.ecommerce.products.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.Product;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    public Optional<Product> findByUuidWithPromotions(UUID uuid) {
        return find("from Product p left join fetch p.promotions where p.uuid = ?1", uuid)
                .firstResultOptional();
    }

    public Product findByUuid(UUID uuid) {
        return find("uuid", uuid).firstResult();
    }

    public List<Product> listWithPromotions(int pageIndex, int pageSize) {
        return find("from Product p left join fetch p.promotions")
                .page(pageIndex, pageSize).list();
    }

    /**
     * Lists products filed under a category or subcategory, with their promotions primed. A
     * {@code subcategoryId} matches products filed directly under that subcategory; otherwise a
     * {@code categoryId} matches products filed directly under it <em>and</em> those under any of
     * its subcategories (the leaf's parent). The category is joined only to filter, so
     * {@code distinct} collapses the per-category-link row duplication.
     */
    public List<Product> listByCategoryWithPromotions(Long categoryId, Long subcategoryId,
            int pageIndex, int pageSize) {
        if (subcategoryId != null) {
            return find("select distinct p from Product p left join fetch p.promotions "
                    + "join p.categories c where c.id = ?1", subcategoryId)
                    .page(pageIndex, pageSize).list();
        }
        return find("select distinct p from Product p left join fetch p.promotions "
                + "join p.categories c left join c.parent parent "
                + "where c.id = ?1 or parent.id = ?1", categoryId)
                .page(pageIndex, pageSize).list();
    }

    /**
     * Primes the persistence context with the product's categories via a second fetch-join query.
     * Loading promotions and categories in one query would raise {@code MultipleBagFetchException},
     * so callers run this against the same session to initialize categories from the session cache.
     */
    public void primeCategories(Long productId) {
        find("from Product p left join fetch p.categories where p.id = ?1", productId).firstResult();
    }

    /**
     * Bulk variant of {@link #primeCategories(Long)} - initializes categories for several products
     * in one query, avoiding {@code MultipleBagFetchException} from a single promotions+categories
     * fetch.
     */
    public void primeCategories(List<Long> productIds) {
        find("from Product p left join fetch p.categories where p.id in ?1", productIds).list();
    }

    public List<Product> findByCriteria(Map<String, QueryCriteria> params, int pageIndex, int pageSize) {
        return find(buildCriteriaQuery(params), toQueryParams(params))
                .page(pageIndex, pageSize).list();
    }

    public void merge(Product product) {
        getEntityManager().merge(product);
    }

    private String buildCriteriaQuery(Map<String, QueryCriteria> params) {
        var query = new StringBuilder("1=1");
        params.forEach((key, criteria) -> {
            if (criteria.operator() == QueryCriteria.Operator.LIKE) {
                // Text search is case-insensitive: compare both sides lower-cased.
                query.append(" and lower(").append(key).append(")")
                        .append(criteria.operator().sql()).append("lower(:").append(key).append(")");
            } else {
                query.append(" and ").append(key)
                        .append(criteria.operator().sql()).append(":").append(key);
            }
        });
        return query.toString();
    }

    private Map<String, Object> toQueryParams(Map<String, QueryCriteria> params) {
        return params.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));
    }

    /**
     * Takes {@code quantity} out of a product's stock, but only if that much is there.
     *
     * <p>The check is part of the write rather than a read followed by a write: two orders reaching
     * the last unit together would both pass a check made beforehand, and the catalog would sell
     * stock it does not have.
     *
     * @return true when the stock was taken
     */
    public boolean decrementStockIfAvailable(UUID uuid, int quantity) {
        return update("stock = stock - ?1 where uuid = ?2 and stock >= ?1", quantity, uuid) == 1;
    }

    /** Puts held stock back on sale. */
    public void incrementStock(UUID uuid, int quantity) {
        update("stock = stock + ?1 where uuid = ?2", quantity, uuid);
    }
}
