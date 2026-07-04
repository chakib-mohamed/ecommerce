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
import the.chak.ecommerce.products.control.exceptions.CategoryHasChildrenException;
import the.chak.ecommerce.products.control.exceptions.InvalidCategoryParentException;
import the.chak.ecommerce.products.control.exceptions.ParentCategoryNotFoundException;
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

    public Category saveCategory(Category category, Long parentId) {
        if (!findByCriteria(Map.of("label", new Criteria(Criteria.Operator.EQUALS, category.getLabel()))).isEmpty()) {
            throw new CategoryAlreadyExistsException(category.getLabel());
        }
        category.setParent(resolveParent(parentId));
        categoryRepository.persist(category);
        recordCategoryMutation(MetricNames.OP_CREATE);
        return category;
    }

    public void updateCategory(Category category, Long parentId) {
        var existing = categoryRepository.findById(category.id);
        if (existing != null) {
            if (parentId != null && parentId.equals(category.id)) {
                throw new InvalidCategoryParentException(category.id);
            }
            category.setParent(resolveParent(parentId));
            categoryRepository.merge(category);
            recordCategoryMutation(MetricNames.OP_UPDATE);
        }
    }

    public void deleteCategory(Long categoryID) {
        Category category = categoryRepository.findById(categoryID);
        if (category != null && !category.getSubCategories().isEmpty()) {
            throw new CategoryHasChildrenException(categoryID);
        }
        categoryRepository.deleteById(categoryID);
        recordCategoryMutation(MetricNames.OP_DELETE);
    }

    /**
     * Resolves a parent-category reference to a managed entity, or {@code null} for a top-level
     * category. Rejects a {@code parentId} that does not match an existing category.
     */
    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = categoryRepository.findById(parentId);
        if (parent == null) {
            throw new ParentCategoryNotFoundException(parentId);
        }
        return parent;
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
