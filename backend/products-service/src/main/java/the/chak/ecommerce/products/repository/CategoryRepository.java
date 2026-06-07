package the.chak.ecommerce.products.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.entity.Category;

@ApplicationScoped
public class CategoryRepository implements PanacheRepository<Category> {

    public List<Category> findByCriteria(Map<String, Criteria> params, int pageIndex, int pageSize) {
        return find(buildCriteriaQuery(params), toQueryParams(params))
                .page(pageIndex, pageSize).list();
    }

    public List<Category> findByCriteria(Map<String, Criteria> params) {
        return find(buildCriteriaQuery(params), toQueryParams(params)).list();
    }

    public void merge(Category category) {
        getEntityManager().merge(category);
    }

    private String buildCriteriaQuery(Map<String, Criteria> params) {
        var query = new StringBuilder("1=1");
        params.forEach((key, criteria) -> query.append(" and ").append(key)
                .append(criteria.getOperator().getValue()).append(" :").append(key));
        return query.toString();
    }

    private Map<String, Object> toQueryParams(Map<String, Criteria> params) {
        return params.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
    }
}
