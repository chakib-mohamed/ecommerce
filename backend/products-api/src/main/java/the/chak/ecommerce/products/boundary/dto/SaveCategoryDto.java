package the.chak.ecommerce.products.boundary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveCategoryDto {

    @NotBlank
    @Size(max = 100)
    private String label;
}
