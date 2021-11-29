package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.entity.Category;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

@Transactional
@ApplicationScoped
public class CategoryService {

    public Category saveCategory(Category category) {
        category.persist();
       return category;
    }

    public void deleteCategory(Long categoryID) {
        Category.deleteById(categoryID);
    }


    public List<Category> findByCriteria(Map<String, Object> params) {
        var query = new StringBuilder("1=1");
        params.forEach(
                (key, value) -> {
                    query.append(" and ").append(key).append("=").append(" :").append(key);
                }
        );

        return Category.list(query.toString(), params);
    }

}
