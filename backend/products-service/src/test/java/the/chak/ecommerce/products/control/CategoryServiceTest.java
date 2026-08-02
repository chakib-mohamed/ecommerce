package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import the.chak.ecommerce.products.control.exceptions.InvalidCategoryParentException;
import the.chak.ecommerce.products.control.exceptions.ParentCategoryNotFoundException;
import the.chak.ecommerce.products.control.exceptions.CategoryHasChildrenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.control.exceptions.CategoryAlreadyExistsException;
import the.chak.ecommerce.products.entity.Category;
import the.chak.ecommerce.products.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    CategoryService categoryService;

    @Mock
    CategoryRepository categoryRepository;

    // A real registry so category mutation counters are recorded and assertable.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Persists and returns the category when its label is new")
    void saveCategory_newLabel_persistsAndReturnsCategory() {
        // given
        Category category = new Category();
        category.setLabel("Electronics");
        when(categoryRepository.findByCriteria(anyMap())).thenReturn(List.of());

        // when
        Category result = categoryService.saveCategory(category, null);

        // then
        assertEquals("Electronics", result.getLabel());
        verify(categoryRepository).persist(category);
    }

    @Test
    @DisplayName("Throws CategoryAlreadyExistsException when the label already exists")
    void saveCategory_duplicateLabel_throwsCategoryAlreadyExistsException() {
        // given
        String label = "Electronics";
        Category category = new Category();
        category.setLabel(label);
        when(categoryRepository.findByCriteria(anyMap())).thenReturn(List.of(new Category()));

        // when & then
        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.saveCategory(category, null));
    }

    @Test
    @DisplayName("Merges the changes when the category id exists")
    void updateCategory_existingId_mergesChanges() {
        // given
        Long id = 1L;
        Category update = new Category();
        update.id = id;
        update.setLabel("Updated Label");

        when(categoryRepository.findById(id)).thenReturn(new Category());

        // when
        categoryService.updateCategory(update, null);

        // then
        verify(categoryRepository).merge(update);
    }

    @Test
    @DisplayName("Does not merge when the category id does not exist")
    void updateCategory_nonExistentId_doesNothing() {
        // given
        Long id = 999L;
        Category ghost = new Category();
        ghost.id = id;

        when(categoryRepository.findById(id)).thenReturn(null);

        // when
        categoryService.updateCategory(ghost, null);

        // then
        verify(categoryRepository, never()).merge(ghost);
    }

    @Test
    @DisplayName("Links the category to its new parent when a parent id is supplied")
    void updateCategory_withParentId_linksResolvedParent() {
        // given
        Long id = 1L;
        Long parentId = 2L;
        Category update = new Category();
        update.id = id;
        Category parent = new Category();
        parent.id = parentId;

        when(categoryRepository.findById(id)).thenReturn(new Category());
        when(categoryRepository.findById(parentId)).thenReturn(parent);

        // when
        categoryService.updateCategory(update, parentId);

        // then
        assertEquals(parent, update.getParent());
        verify(categoryRepository).merge(update);
    }

    @Test
    @DisplayName("Refuses to make a category its own parent")
    void updateCategory_parentIsItself_throwsInvalidCategoryParentException() {
        // given
        Long id = 1L;
        Category update = new Category();
        update.id = id;

        when(categoryRepository.findById(id)).thenReturn(new Category());

        // when / then - a self-reference would make the tree cyclic
        assertThrows(InvalidCategoryParentException.class,
                () -> categoryService.updateCategory(update, id));
        verify(categoryRepository, never()).merge(update);
    }

    @Test
    @DisplayName("Refuses a parent id that matches no category")
    void updateCategory_unknownParentId_throwsParentCategoryNotFoundException() {
        // given
        Long id = 1L;
        Long parentId = 404L;
        Category update = new Category();
        update.id = id;

        when(categoryRepository.findById(id)).thenReturn(new Category());
        when(categoryRepository.findById(parentId)).thenReturn(null);

        // when / then
        assertThrows(ParentCategoryNotFoundException.class,
                () -> categoryService.updateCategory(update, parentId));
        verify(categoryRepository, never()).merge(update);
    }

    @Test
    @DisplayName("Refuses to delete a category that still has subcategories")
    void deleteCategory_withSubCategories_throwsCategoryHasChildrenException() {
        // given - deleting would orphan the children
        Long id = 1L;
        Category parent = new Category();
        parent.id = id;
        Category child = new Category();
        child.id = 2L;
        parent.setSubCategories(new ArrayList<>(List.of(child)));

        when(categoryRepository.findById(id)).thenReturn(parent);

        // when / then
        assertThrows(CategoryHasChildrenException.class, () -> categoryService.deleteCategory(id));
        verify(categoryRepository, never()).deleteById(id);
    }

    @Test
    @DisplayName("Removes a category that exists and has no subcategories")
    void deleteCategory_existingLeafCategory_removesIt() {
        // given
        Long id = 1L;
        Category leaf = new Category();
        leaf.id = id;
        leaf.setSubCategories(new ArrayList<>());

        when(categoryRepository.findById(id)).thenReturn(leaf);

        // when
        categoryService.deleteCategory(id);

        // then
        verify(categoryRepository).deleteById(id);
    }

    @Test
    @DisplayName("Removes the category from the database by id")
    void deleteCategory_removesFromDatabase() {
        // given
        Long id = 1L;

        // when
        categoryService.deleteCategory(id);

        // then
        verify(categoryRepository).deleteById(id);
    }

    @Test
    @DisplayName("Returns matching categories when filtering on an allowed field")
    void findByCriteria_withAllowedField_returnsMatchingResults() {
        // given
        String label = "Electronics";
        Map<String, Criteria> params = Map.of("label", new Criteria(Criteria.Operator.EQUALS, label));
        when(categoryRepository.findByCriteria(anyMap())).thenReturn(List.of(new Category()));

        // when
        List<Category> results = categoryService.findByCriteria(params);

        // then
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Throws BadRequestException when filtering on a field that is not allowed")
    void findByCriteria_withInvalidField_throwsBadRequestException() {
        // given
        Map<String, Criteria> params = Map.of("unknown_field", new Criteria(Criteria.Operator.EQUALS, "x"));

        // when & then
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(params));
    }

    @Test
    @DisplayName("Returns a page of categories when paginating a filter on an allowed field")
    void findByCriteria_paginatedWithAllowedField_returnsPage() {
        // given
        Map<String, Criteria> params = Map.of("label", new Criteria(Criteria.Operator.LIKE, "Tech%"));
        when(categoryRepository.findByCriteria(anyMap(), eq(0), eq(2)))
                .thenReturn(List.of(new Category(), new Category()));

        // when
        List<Category> page = categoryService.findByCriteria(params, 0, 2);

        // then
        assertEquals(2, page.size());
    }

    @Test
    @DisplayName("Throws BadRequestException when paginating a filter on a field that is not allowed")
    void findByCriteria_paginatedWithInvalidField_throwsBadRequestException() {
        // given
        Map<String, Criteria> params = Map.of("bad_field", new Criteria(Criteria.Operator.EQUALS, "x"));

        // when & then
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(params, 0, 10));
    }

    // --metrics ------------------------------------------------------------

    @Test
    @DisplayName("Counts a create mutation when a category with a new label is saved")
    void saveCategory_newLabel_recordsCreateMutation() {
        // given
        Category category = new Category();
        category.setLabel("Electronics");
        when(categoryRepository.findByCriteria(anyMap())).thenReturn(List.of());

        // when
        categoryService.saveCategory(category, null);

        // then
        assertEquals(1.0,
                meterRegistry.get("catalog.categories.mutations").tag("op", "create").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Records no create mutation when the category label already exists")
    void saveCategory_duplicateLabel_recordsNoCreateMutation() {
        // given
        Category category = new Category();
        category.setLabel("Electronics");
        when(categoryRepository.findByCriteria(anyMap())).thenReturn(List.of(new Category()));

        // when & then
        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.saveCategory(category, null));
        assertNull(meterRegistry.find("catalog.categories.mutations").counter());
    }

    @Test
    @DisplayName("Counts an update mutation when an existing category is merged")
    void updateCategory_existingId_recordsUpdateMutation() {
        // given
        Long id = 1L;
        Category update = new Category();
        update.id = id;
        update.setLabel("Updated Label");
        when(categoryRepository.findById(id)).thenReturn(new Category());

        // when
        categoryService.updateCategory(update, null);

        // then
        assertEquals(1.0,
                meterRegistry.get("catalog.categories.mutations").tag("op", "update").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Records no update mutation when the category id does not exist")
    void updateCategory_nonExistentId_recordsNoUpdateMutation() {
        // given
        Long id = 999L;
        Category ghost = new Category();
        ghost.id = id;
        when(categoryRepository.findById(id)).thenReturn(null);

        // when
        categoryService.updateCategory(ghost, null);

        // then
        assertNull(meterRegistry.find("catalog.categories.mutations").counter());
    }

    @Test
    @DisplayName("Counts a delete mutation when a category is removed")
    void deleteCategory_recordsDeleteMutation() {
        // given
        Long id = 1L;

        // when
        categoryService.deleteCategory(id);

        // then
        assertEquals(1.0,
                meterRegistry.get("catalog.categories.mutations").tag("op", "delete").counter().count(),
                0.001);
    }
}
