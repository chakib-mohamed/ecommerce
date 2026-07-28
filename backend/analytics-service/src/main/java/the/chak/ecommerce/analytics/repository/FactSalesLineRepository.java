package the.chak.ecommerce.analytics.repository;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import the.chak.ecommerce.analytics.entity.FactSalesLine;

/**
 * Sales fact store. Holds the dashboard's rollups, which are plain group-by aggregations because
 * line revenue is already resolved on each row.
 */
@ApplicationScoped
public class FactSalesLineRepository implements PanacheRepository<FactSalesLine> {

    /** Revenue per month from {@code fromMonth} onward; months without sales are simply absent. */
    public List<MonthlyRevenue> revenueByMonth(String fromMonth) {
        return getEntityManager().createQuery("""
                select new the.chak.ecommerce.analytics.repository.MonthlyRevenue(
                        f.orderMonth, sum(f.lineRevenue))
                from FactSalesLine f
                where f.orderMonth >= :fromMonth
                group by f.orderMonth
                order by f.orderMonth
                """, MonthlyRevenue.class)
                .setParameter("fromMonth", fromMonth)
                .getResultList();
    }

    /** Units and revenue per product, best sellers first. */
    public List<ProductAggregate> salesByProduct() {
        return getEntityManager().createQuery("""
                select new the.chak.ecommerce.analytics.repository.ProductAggregate(
                        f.productId, max(f.productTitle), sum(f.units), sum(f.lineRevenue))
                from FactSalesLine f
                group by f.productId
                order by sum(f.units) desc
                """, ProductAggregate.class)
                .getResultList();
    }

    /**
     * Revenue per category, highest first. Left-joined to the dimension so sales of a product the
     * dimension has never seen still count -- they surface with a null category.
     */
    public List<CategoryAggregate> revenueByCategory() {
        return getEntityManager().createQuery("""
                select new the.chak.ecommerce.analytics.repository.CategoryAggregate(
                        d.categoryId, d.categoryLabel, sum(f.lineRevenue))
                from FactSalesLine f
                left join DimProduct d on d.productId = f.productId
                group by d.categoryId, d.categoryLabel
                order by sum(f.lineRevenue) desc
                """, CategoryAggregate.class)
                .getResultList();
    }

    /** Total revenue across every recorded line; zero when the warehouse is empty. */
    public double totalRevenue() {
        return getEntityManager().createQuery(
                "select coalesce(sum(f.lineRevenue), 0) from FactSalesLine f", Double.class)
                .getSingleResult();
    }

    /** Drops an order's lines so it can be re-ingested cleanly on redelivery. */
    public long deleteByOrderId(String orderId) {
        return delete("orderId", orderId);
    }
}
