package the.chak.ecommerce.orders.boundary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchOrdersCommand {

    @NotBlank
    String userID;
    @Min(0)
    Integer offset;
    @Min(1)
    @Max(100)
    Integer limit;
    String sortBy;
}
