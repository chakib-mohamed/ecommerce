package the.chak.ecommerce.orders.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;

@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {

    public PagedResult<Order> search(OrderSearch search) {
        var query = "";
        Map<String, Object> params = new HashMap<>();
        if (search.userID() != null && !search.userID().isEmpty()) {
            query += "userID = :userID ";
            params.put("userID", search.userID());
        }
        if (search.productID() != null && !search.productID().isEmpty()) {
            query += (query.isEmpty() ? "" : "and ") + "products.productID = :productID ";
            params.put("productID", search.productID());
        }
        if (search.statuses() != null && !search.statuses().isEmpty()) {
            // Names, not the enum values: the field is stored as a string, and matching against
            // enum constants leaves the query silently returning nothing.
            query += (query.isEmpty() ? "" : "and ") + "status in :statuses ";
            params.put("statuses", search.statuses().stream().map(Enum::name).toList());
        }

        // Newest first, and above all *deterministic*. Without a sort Mongo returns natural order,
        // which paging then slices: the same order can appear on two pages of one walk, or on
        // none, because nothing pins a record to a position between the two queries. That it
        // usually came back insertion-ordered is an accident of how the collection happens to be
        // stored, not something the driver promises.
        var panacheQuery = find(query, Sort.by("creationDate", Sort.Direction.Descending), params);

        if (search.limit() != null && search.offset() != null) {
            panacheQuery.page(Page.of(search.offset(), search.limit()));
        }

        long totalCount = panacheQuery.count();
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new PagedResult<>(totalCount, result);
    }

    /**
     * Priced-but-uncommitted orders older than the cutoff.
     *
     * <p>Ordered oldest first and bounded, for the same reasons as the saga sweep: a backlog drains
     * in the order it accumulated, and one pass cannot monopolise the scheduler.
     */
    public java.util.List<Order> findExpiredInitiated(java.time.LocalDateTime cutoff, int limit) {
        // The name, not the constant - for the same reason as the status filter in search() above.
        // The field holds a string, and comparing it to an enum matches nothing while looking
        // exactly like a query that found nothing to match.
        return find("status = ?1 and creationDate < ?2 order by creationDate",
                OrderStatus.INITIATED.name(), cutoff)
                .page(0, limit).list();
    }

    /**
     * Orders whose outstanding saga step is past its deadline.
     *
     * <p>Ordered oldest first so a backlog drains in the order it accumulated, and bounded so one
     * sweep cannot monopolise the scheduler.
     */
    public java.util.List<Order> findExpiredSteps(java.time.Instant now, int limit) {
        return find("stepDeadline != null and stepDeadline < ?1 order by stepDeadline", now)
                .page(0, limit).list();
    }
}
