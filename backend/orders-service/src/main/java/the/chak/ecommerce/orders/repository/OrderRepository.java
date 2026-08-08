package the.chak.ecommerce.orders.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.entity.Order;

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

        var panacheQuery = find(query, params);

        if (search.limit() != null && search.offset() != null) {
            panacheQuery.page(Page.of(search.offset(), search.limit()));
        }

        long totalCount = panacheQuery.count();
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new PagedResult<>(totalCount, result);
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
