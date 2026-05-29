package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @Mock
    EntityManager em;

    @Test
    void saveCategory_newLabel_persistsAndReturnsCategory() {
        // given
        Category category = new Category();
        category.setLabel("Electronics");

        PanacheQuery<Category> query = mock(PanacheQuery.class);
        when(query.list()).thenReturn(List.of());
        when(categoryRepository.find(anyString(), anyMap())).thenReturn(query);

        // when
        Category result = categoryService.saveCategory(category);

        // then
        assertEquals("Electronics", result.getLabel());
        verify(categoryRepository).persist(category);
    }

    @Test
    void saveCategory_duplicateLabel_throwsCategoryAlreadyExistsException() {
        // given
        String label = "Electronics";
        Category category = new Category();
        category.setLabel(label);

        PanacheQuery<Category> query = mock(PanacheQuery.class);
        when(query.list()).thenReturn(List.of(new Category()));
        when(categoryRepository.find(anyString(), anyMap())).thenReturn(query);

        // when & then
        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.saveCategory(category));
    }

    @Test
    void updateCategory_existingId_mergesChanges() {
        // given
        Long id = 1L;
        Category update = new Category();
        update.id = id;
        update.setLabel("Updated Label");

        when(categoryRepository.findById(id)).thenReturn(new Category());

        // when
        categoryService.updateCategory(update);

        // then
        verify(em).merge(update);
    }

    @Test
    void updateCategory_nonExistentId_doesNothing() {
        // given
        Long id = 999L;
        Category ghost = new Category();
        ghost.id = id;

        when(categoryRepository.findById(id)).thenReturn(null);

        // when
        categoryService.updateCategory(ghost);

        // then
        verify(em, never()).merge(ghost);
    }

    @Test
    void deleteCategory_removesFromDatabase() {
        // given
        Long id = 1L;

        // when
        categoryService.deleteCategory(id);

        // then
        verify(categoryRepository).deleteById(id);
    }

    @Test
    void findByCriteria_withAllowedField_returnsMatchingResults() {
        // given
        String label = "Electronics";
        Map<String, Criteria> params = Map.of("label", new Criteria(Criteria.Operator.EQUALS, label));

        PanacheQuery<Category> query = mock(PanacheQuery.class);
        when(query.list()).thenReturn(List.of(new Category()));
        when(categoryRepository.find(anyString(), anyMap())).thenReturn(query);

        // when
        List<Category> results = categoryService.findByCriteria(params);

        // then
        assertEquals(1, results.size());
    }

    @Test
    void findByCriteria_withInvalidField_throwsBadRequestException() {
        // given
        Map<String, Criteria> params = Map.of("unknown_field", new Criteria(Criteria.Operator.EQUALS, "x"));

        // when & then
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(params));
    }

    @Test
    void findByCriteria_paginatedWithAllowedField_returnsPage() {
        // given
        Map<String, Criteria> params = Map.of("label", new Criteria(Criteria.Operator.LIKE, "Tech%"));

        PanacheQuery<Category> query = mock(PanacheQuery.class);
        when(query.page(0, 2)).thenReturn(query);
        when(query.list()).thenReturn(List.of(new Category(), new Category()));
        when(categoryRepository.find(anyString(), anyMap())).thenReturn(query);

        // when
        List<Category> page = categoryService.findByCriteria(params, 0, 2);

        // then
        assertEquals(2, page.size());
    }

    @Test
    void findByCriteria_paginatedWithInvalidField_throwsBadRequestException() {
        // given
        Map<String, Criteria> params = Map.of("bad_field", new Criteria(Criteria.Operator.EQUALS, "x"));

        // when & then
        assertThrows(BadRequestException.class,
                () -> categoryService.findByCriteria(params, 0, 10));
    }
}
