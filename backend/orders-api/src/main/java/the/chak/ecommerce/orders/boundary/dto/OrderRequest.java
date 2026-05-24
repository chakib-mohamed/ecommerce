package the.chak.ecommerce.orders.boundary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class OrderRequest {

    private String id;

    @NotEmpty
    @Valid
    private List<ProductVO> products;

}
