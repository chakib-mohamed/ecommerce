package the.chak.ecommerce.orders.boundary.dto;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchOrdersCommand {

    @NotBlank
    String userID;
    String productID;
    /**
     * States to restrict the search to. Null or empty means every state, so a caller that does not
     * care - order history, which shows a buyer everything they placed - is unaffected.
     */
    List<OrderStatus> statuses;
    @Min(0)
    Integer offset;
    @Min(1)
    @Max(100)
    Integer limit;
    String sortBy;
}
