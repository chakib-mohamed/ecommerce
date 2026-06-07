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

        var panacheQuery = find(query, params);

        if (search.limit() != null && search.offset() != null) {
            panacheQuery.page(Page.of(search.offset(), search.limit()));
        }

        long totalCount = panacheQuery.count();
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new PagedResult<>(totalCount, result);
    }
}
