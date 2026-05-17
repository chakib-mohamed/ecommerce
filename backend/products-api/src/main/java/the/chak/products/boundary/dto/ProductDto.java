package chakmed.ecommerce.products.boundary.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
public class ProductDto {
    private UUID uuid;
    private String description;
    private String image;
    private Double price;
    private String title;

    private List<PromotionDto> promotions;
    private List<CategoryDto> categories;

}
