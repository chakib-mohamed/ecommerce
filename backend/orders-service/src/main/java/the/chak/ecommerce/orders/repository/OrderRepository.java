package the.chak.ecommerce.orders.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.orders.entity.Order;

@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {

    public Tuple<Long, List<Order>> search(SearchOrdersCommand searchOrdersCommand) {
        var query = "";
        Map<String, Object> params = new HashMap<>();
        if (searchOrdersCommand.getUserID() != null && !searchOrdersCommand.getUserID().isEmpty()) {
            query += "userID = :userID ";
            params.put("userID", searchOrdersCommand.getUserID());
        }

        var panacheQuery = find(query, params);

        if (searchOrdersCommand.getLimit() != null && searchOrdersCommand.getOffset() != null) {
            panacheQuery
                    .page(Page.of(searchOrdersCommand.getOffset(), searchOrdersCommand.getLimit()));
        }

        long totalCount = panacheQuery.count();
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new Tuple<>(totalCount, result);
    }
}
