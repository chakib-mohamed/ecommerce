package chakmed.ecommerce.orders.boundary.command;

import chakmed.ecommerce.orders.entity.ProductVO;
import lombok.Data;

import javax.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateOrderCommand {

    private String id;
    private String cardNumber;
    @JsonbDateFormat(value = "yyyy-MM-dd'T'HH:mm:ssX")
    private LocalDateTime creationDate;
    private String expirationDate;
    private Double price;
    private List<ProductVO> products;
    private String status;
    private String userID;
    private String validationNumber;

}
