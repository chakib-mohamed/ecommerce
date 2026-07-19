package the.chak.ecommerce.products.boundary.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewDto {

    private String id;
    private String productId;
    private String reviewer;
    private Integer stars;
    private String text;
    private LocalDateTime createdAt;
}
