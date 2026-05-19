package the.chak.ecommerce.products.boundary.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductMongoEntityDto {

    private String id;
    private String productId;
    private String description;
    private String imageKey;
    private Double price;
    private String title;

    private List<PromotionDto> promotions;
    private List<CategoryDto> categories;

}
