package the.chak.ecommerce.products.control;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.exceptions.CategoryAlreadyExistsException;
import the.chak.ecommerce.products.entity.Category;

@Transactional
@ApplicationScoped
public class CategoryService {

    private static final Set<String> ALLOWED_CATEGORY_FIELDS = Set.of("id", "label");

    @Inject
    EntityManager em;

    public Category saveCategory(Category category) {
        if (!findByCriteria(Map.of("label", new Criteria(Criteria.Operator.EQUALS, category.getLabel()))).isEmpty()) {
            throw new CategoryAlreadyExistsException(category.getLabel());
        }
        category.persist();
        return category;
    }

    public void updateCategory(Category category) {
        var existing = Category.findById(category.id);
        if (existing != null) {
            em.merge(category);
        }
    }

    public void deleteCategory(Long categoryID) {
        Category.deleteById(categoryID);
    }

    public List<Category> findByCriteria(Map<String, Criteria> params, int pageIndex, int pageSize) {
        var query = new StringBuilder("1=1");
        params.forEach((key, criteria) -> {
            if (!ALLOWED_CATEGORY_FIELDS.contains(key)) {
                throw new BadRequestException("Invalid search field: " + key);
            }
            query.append(" and ").append(key)
                    .append(criteria.getOperator().getValue()).append(" :").append(key);
        });

        return Category.find(query.toString(),
                        params.entrySet().stream().collect(
                                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())))
                .page(pageIndex, pageSize)
                .list();
    }

    public List<Category> findByCriteria(Map<String, Criteria> params) {
        var query = new StringBuilder("1=1");
        params.forEach((key, criteria) -> {
            if (!ALLOWED_CATEGORY_FIELDS.contains(key)) {
                throw new BadRequestException("Invalid search field: " + key);
            }
            query.append(" and ").append(key)
                    .append(criteria.getOperator().getValue()).append(" :").append(key);
        });

        return Category.find(query.toString(),
                        params.entrySet().stream().collect(
                                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())))
                .list();
    }
}
