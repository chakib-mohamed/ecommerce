package the.chak.ecommerce.products.boundary.mapper;

import java.util.List;
import org.hibernate.Hibernate;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.SaveCategoryDto;
import the.chak.ecommerce.products.entity.Category;

@Mapper(componentModel = "jakarta")
public interface CategoryMapper extends BaseMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategoryDto toDto(Category category);

    /**
     * Only nest {@code sub_categories} when the children were loaded for this read (the category-tree
     * endpoint primes them). On the flat read paths (search/create/update) the collection is a lazy
     * proxy left uninitialized, so it is skipped rather than touched outside its transaction.
     */
    @Condition
    default boolean hasLoadedChildren(List<Category> subCategories) {
        return Hibernate.isInitialized(subCategories);
    }

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    Category toEntity(CategoryDto categoryDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    Category toEntity(SaveCategoryDto saveCategoryDto);
}
