package the.chak.ecommerce.products.boundary.dto;

import java.util.List;
import java.util.UUID;
import lombok.Data;
import the.chak.ecommerce.products.boundary.validation.ValidImage;

@Data
public class ProductDto {
    private UUID uuid;
    private String description;

    @ValidImage
    @jakarta.json.bind.annotation.JsonbTypeAdapter(the.chak.ecommerce.products.boundary.ByteArrayBase64Adapter.class)
    private byte[] image;

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    private String imageKey;
    private Double price;
    private String title;

    private List<PromotionDto> promotions;
    private List<CategoryDto> categories;

}
