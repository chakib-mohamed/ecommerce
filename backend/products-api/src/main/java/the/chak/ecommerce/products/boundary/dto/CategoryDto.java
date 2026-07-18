package the.chak.ecommerce.products.boundary.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryDto {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String label;

    private Long parentId;

    @Valid
    private List<CategoryDto> subCategories;

}
