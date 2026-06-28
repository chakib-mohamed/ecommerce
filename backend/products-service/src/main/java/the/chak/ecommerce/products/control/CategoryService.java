package the.chak.ecommerce.products.control;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.exceptions.CategoryAlreadyExistsException;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.repository.CategoryRepository;

@Transactional
@ApplicationScoped
public class CategoryService {

    private static final Set<String> ALLOWED_CATEGORY_FIELDS = Set.of("id", "label");

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    MeterRegistry meterRegistry;

    public Category saveCategory(Category category) {
        if (!findByCriteria(Map.of("label", new Criteria(Criteria.Operator.EQUALS, category.getLabel()))).isEmpty()) {
            throw new CategoryAlreadyExistsException(category.getLabel());
        }
        categoryRepository.persist(category);
        recordCategoryMutation(MetricNames.OP_CREATE);
        return category;
    }

    public void updateCategory(Category category) {
        var existing = categoryRepository.findById(category.id);
        if (existing != null) {
            categoryRepository.merge(category);
            recordCategoryMutation(MetricNames.OP_UPDATE);
        }
    }

    public void deleteCategory(Long categoryID) {
        categoryRepository.deleteById(categoryID);
        recordCategoryMutation(MetricNames.OP_DELETE);
    }

    private void recordCategoryMutation(String op) {
        meterRegistry.counter(MetricNames.CATALOG_CATEGORIES_MUTATIONS, MetricNames.TAG_OP, op).increment();
    }

    /**
     * Returns the top-level categories with their child categories initialized, so the boundary can
     * serialize the nested tree without touching lazy associations outside this transaction. The
     * two-level catalog terminates the priming at grandchildren (empty for leaf subcategories).
     */
    public List<Category> getRootCategories(int pageIndex, int pageSize) {
        List<Category> roots = categoryRepository.findRoots(pageIndex, pageSize);
        roots.forEach(root -> {
            List<Category> children = root.getSubCategories();
            children.size();
            children.forEach(child -> child.getSubCategories().size());
        });
        return roots;
    }

    public List<Category> findByCriteria(Map<String, Criteria> params, int pageIndex, int pageSize) {
        validateFields(params);
        return categoryRepository.findByCriteria(
                CriteriaMapper.toQueryCriteria(params), pageIndex, pageSize);
    }

    public List<Category> findByCriteria(Map<String, Criteria> params) {
        validateFields(params);
        return categoryRepository.findByCriteria(CriteriaMapper.toQueryCriteria(params));
    }

    private void validateFields(Map<String, Criteria> params) {
        params.keySet().forEach(key -> {
            if (!ALLOWED_CATEGORY_FIELDS.contains(key)) {
                throw new BadRequestException("Invalid search field: " + key);
            }
        });
    }
}
