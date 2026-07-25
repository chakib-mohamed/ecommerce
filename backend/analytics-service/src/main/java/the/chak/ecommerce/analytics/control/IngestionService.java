package the.chak.ecommerce.analytics.control;

import java.time.format.DateTimeFormatter;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import the.chak.ecommerce.analytics.entity.DimProduct;
import the.chak.ecommerce.analytics.entity.FactSalesLine;
import the.chak.ecommerce.analytics.repository.DimProductRepository;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

/**
 * Writes the event streams into the warehouse.
 *
 * <p>Every method is idempotent: delivery is at-least-once, so the same order or product can
 * arrive more than once and must not double-count.
 */
@ApplicationScoped
public class IngestionService {

    private static final Logger LOG = Logger.getLogger(IngestionService.class);
    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final double FULL_PRICE = 100d;

    @Inject
    FactSalesLineRepository factRepository;

    @Inject
    DimProductRepository dimRepository;

    /**
     * Records a completed order's lines, replacing any previously recorded for that order.
     *
     * <p>Clearing first is what makes redelivery safe: the second copy of an order rewrites the
     * same rows instead of adding a duplicate set.
     */
    @Transactional
    public void ingestOrder(OrderDTO order) {
        factRepository.deleteByOrderId(order.getId());

        List<ProductVO> lines = order.getProducts() == null ? List.of() : order.getProducts();
        for (ProductVO line : lines) {
            factRepository.persist(toFact(order, line));
        }
        LOG.infof("Ingested order orderId=%s lines=%d", order.getId(), lines.size());
    }

    private FactSalesLine toFact(OrderDTO order, ProductVO line) {
        FactSalesLine fact = new FactSalesLine();
        fact.setOrderId(order.getId());
        fact.setProductId(line.getProductID());
        fact.setProductTitle(line.getTitle());
        fact.setUnits(line.getQty());
        fact.setUnitPrice(line.getPrice());
        fact.setPercentageOff(line.getPercentageOff());
        fact.setLineRevenue(lineRevenue(line));
        fact.setOrderDate(order.getCreationDate());
        fact.setOrderMonth(order.getCreationDate().format(MONTH_BUCKET));
        fact.setUserId(order.getUserID());
        return fact;
    }

    /**
     * Revenue is resolved from the price and discount frozen onto the order, so the figure stays
     * put when the product's live price later moves.
     */
    private double lineRevenue(ProductVO line) {
        double discount = line.getPercentageOff() == null ? 0d : line.getPercentageOff();
        return line.getQty() * line.getPrice() * ((FULL_PRICE - discount) / FULL_PRICE);
    }

    /** Creates or refreshes a product's dimension row. */
    @Transactional
    public void upsertProduct(ProductDto product) {
        String productId = product.getUuid().toString();
        dimRepository.findByIdOptional(productId).ifPresentOrElse(
                existing -> apply(product, existing),
                () -> {
                    DimProduct row = new DimProduct();
                    row.setProductId(productId);
                    apply(product, row);
                    dimRepository.persist(row);
                });
    }

    private void apply(ProductDto product, DimProduct row) {
        CategoryDto filed = filedUnder(product);
        row.setTitle(product.getTitle());
        row.setCategoryId(filed == null ? product.getCategoryId() : filed.getId());
        row.setCategoryLabel(filed == null ? null : filed.getLabel());
        row.setSubcategoryId(product.getSubcategoryId());
        row.setSubcategoryLabel(labelOf(product.getCategories(), product.getSubcategoryId()));
        row.setDeleted(false);
    }

    /**
     * The category a product is actually filed under.
     *
     * <p>Product events carry the filed categories, not the category/subcategory ids used when
     * submitting a product -- those are submission-side and arrive empty, so reading them alone
     * would leave every sale uncategorized.
     */
    private CategoryDto filedUnder(ProductDto product) {
        List<CategoryDto> categories = product.getCategories();
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories.get(0);
    }

    /** Resolves a category's display label from the ones carried alongside the product. */
    private String labelOf(List<CategoryDto> categories, Long categoryId) {
        if (categories == null || categoryId == null) {
            return null;
        }
        return categories.stream()
                .filter(category -> categoryId.equals(category.getId()))
                .map(CategoryDto::getLabel)
                .findFirst()
                .orElse(null);
    }

    /**
     * Flags a product as removed from the catalog, keeping its row so sales made while it was on
     * sale keep their category.
     */
    @Transactional
    public void markProductDeleted(String productId) {
        dimRepository.findByIdOptional(productId)
                .ifPresent(row -> row.setDeleted(true));
    }
}
