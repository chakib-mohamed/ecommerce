package chakmed.ecommerce.products.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ProductDTO {
    private Long id;
    private String description;
    private String image;
    private Double price;
    private String title;

    private Set<PromotionDTO> promotions;

    private CategoryDTO category;
}
