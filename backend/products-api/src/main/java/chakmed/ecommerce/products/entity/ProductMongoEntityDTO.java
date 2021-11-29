package chakmed.ecommerce.products.entity;

import lombok.Data;

import java.util.List;

@Data
public class ProductMongoEntityDTO {

    private String id;
    private Long productID;
    private String description;
    private String image;
    private Double price;
    private String title;

    private List<PromotionDTO> promotions;
    private String category;


}
