package the.chak.ecommerce.products.control;

import java.util.Map;
import java.util.stream.Collectors;

import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.repository.QueryCriteria;

/**
 * Maps inbound search {@link Criteria} (boundary contract) onto the repository's neutral
 * {@link QueryCriteria}, so the persistence layer never depends on boundary DTOs. Operators are
 * matched by name; the two enums share the same constants.
 */
final class CriteriaMapper {

    private CriteriaMapper() {
    }

    static Map<String, QueryCriteria> toQueryCriteria(Map<String, Criteria> params) {
        return params.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new QueryCriteria(
                        QueryCriteria.Operator.valueOf(entry.getValue().getOperator().name()),
                        entry.getValue().getValue())));
    }
}
