package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.products.KafkaTestResource;
import the.chak.ecommerce.products.StorageTestResource;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.exceptions.CategoryAlreadyExistsException;
import the.chak.ecommerce.products.entity.Category;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(StorageTestResource.class)
@TestTransaction
class CategoryServiceTest {

    @Inject
    CategoryService categoryService;

    @Test
    void saveCategory_newLabel_persistsAndReturnsCategory() {
        // given
        Category category = new Category();
        category.setLabel("Electronics-" + UUID.randomUUID());

        // when
        Category result = categoryService.saveCategory(category);

        // then
        assertNotNull(result.id);
        assertEquals(category.getLabel(), result.getLabel());
    }

    @Test
    void saveCategory_duplicateLabel_throwsCategoryAlreadyExistsException() {
        // given
        String label = "Duplicate-" + UUID.randomUUID();
        Category first = new Category();
        first.setLabel(label);
        categoryService.saveCategory(first);

        Category second = new Category();
        second.setLabel(label);

        // when / then
        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.saveCategory(second));
    }

    @Test
    void updateCategory_existingId_mergesChanges() {
        // given
        Category category = new Category();
        category.setLabel("Original-" + UUID.randomUUID());
        categoryService.saveCategory(category);
        Long id = category.id;

        Category update = new Category();
        update.id = id;
        update.setLabel("Updated Label");

        // when
        categoryService.updateCategory(update);

        // then
        Category found = Category.findById(id);
        assertEquals("Updated Label", found.getLabel());
    }

    @Test
    void updateCategory_nonExistentId_doesNothing() {
        // given
        Category ghost = new Category();
        ghost.id = Long.MAX_VALUE;
        ghost.setLabel("Ghost");

        // when / then — must not throw
        categoryService.updateCategory(ghost);
        assertNull(Category.findById(Long.MAX_VALUE));
    }

    @Test
    void deleteCategory_removesFromDatabase() {
        // given
        Category category = new Category();
        category.setLabel("ToDelete-" + UUID.randomUUID());
        categoryService.saveCategory(category);
        Long id = category.id;

        // when
        categoryService.deleteCategory(id);

        // then
        assertNull(Category.findById(id));
    }

    @Test
    void findByCriteria_withAllowedField_returnsMatchingResults() {
        // given
        String label = "Findable-" + UUID.randomUUID();
        Category category = new Category();
        category.setLabel(label);
        categoryService.saveCategory(category);

        // when
        List<Category> results = categoryService.findByCriteria(
                Map.of("label", new Criteria(Criteria.Operator.EQUALS, label)));

        // then
        assertEquals(1, results.size());
        assertEquals(label, results.get(0).getLabel());
    }

    @Test
    void findByCriteria_withInvalidField_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(
                        Map.of("unknown_field", new Criteria(Criteria.Operator.EQUALS, "x"))));
    }

    @Test
    void findByCriteria_paginated_withAllowedField_returnsPage() {
        // given — save two categories with a shared prefix so we can filter
        String prefix = "Paged-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Category c = new Category();
            c.setLabel(prefix + "-" + i);
            categoryService.saveCategory(c);
        }

        // when — page size 2, page 0
        List<Category> page = categoryService.findByCriteria(
                Map.of("label", new Criteria(Criteria.Operator.LIKE, prefix + "%")), 0, 2);

        // then
        assertTrue(page.size() <= 2);
    }

    @Test
    void findByCriteria_paginated_withInvalidField_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(
                        Map.of("bad_field", new Criteria(Criteria.Operator.EQUALS, "x")), 0, 10));
    }
}
