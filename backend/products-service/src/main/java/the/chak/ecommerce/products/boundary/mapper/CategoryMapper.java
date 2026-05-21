package the.chak.ecommerce.products.boundary.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.SaveCategoryDto;
import the.chak.ecommerce.products.entity.Category;

@Mapper(componentModel = "jakarta")
public interface CategoryMapper extends BaseMapper {

    CategoryDto toDto(Category category);

    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    Category toEntity(CategoryDto categoryDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "subCategories", ignore = true)
    Category toEntity(SaveCategoryDto saveCategoryDto);
}
