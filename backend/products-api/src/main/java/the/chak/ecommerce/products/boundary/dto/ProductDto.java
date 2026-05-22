package the.chak.ecommerce.products.boundary.dto;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import the.chak.ecommerce.products.boundary.validation.ValidImage;

@Getter
@Setter
public class ProductDto {
    private UUID uuid;
    private String description;

    @ValidImage
    @jakarta.json.bind.annotation.JsonbTypeAdapter(the.chak.ecommerce.products.boundary.ByteArrayBase64Adapter.class)
    private byte[] image;


    private String imageKey;
    private Double price;
    private String title;

    private List<PromotionDto> promotions;
    private List<CategoryDto> categories;

}
