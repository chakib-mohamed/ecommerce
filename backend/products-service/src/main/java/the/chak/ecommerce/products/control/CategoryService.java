package the.chak.ecommerce.products.control;

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

    public Category saveCategory(Category category) {
        if (!findByCriteria(Map.of("label", new Criteria(Criteria.Operator.EQUALS, category.getLabel()))).isEmpty()) {
            throw new CategoryAlreadyExistsException(category.getLabel());
        }
        categoryRepository.persist(category);
        return category;
    }

    public void updateCategory(Category category) {
        var existing = categoryRepository.findById(category.id);
        if (existing != null) {
            categoryRepository.merge(category);
        }
    }

    public void deleteCategory(Long categoryID) {
        categoryRepository.deleteById(categoryID);
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
