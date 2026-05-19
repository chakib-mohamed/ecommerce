package the.chak.ecommerce.orders.boundary.dto;

import java.util.List;
import lombok.Data;

@Data
public class OrderRequest {

    private String id;
    private List<ProductVO> products;

}
