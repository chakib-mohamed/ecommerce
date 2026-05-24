package the.chak.ecommerce.products.boundary.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import the.chak.ecommerce.products.boundary.validation.ValidImage;

@Getter
@Setter
public class ProductDto {
    private UUID uuid;

    @Size(max = 2000)
    private String description;

    @ValidImage
    @jakarta.json.bind.annotation.JsonbTypeAdapter(the.chak.ecommerce.products.boundary.ByteArrayBase64Adapter.class)
    private byte[] image;

    private String imageKey;

    @NotNull
    @Positive
    private Double price;

    @NotBlank
    private String title;

    @Valid
    private List<PromotionDto> promotions;

    @Valid
    private List<CategoryDto> categories;

}
