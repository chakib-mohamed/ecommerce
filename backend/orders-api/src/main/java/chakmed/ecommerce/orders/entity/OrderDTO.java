package chakmed.ecommerce.orders.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.json.bind.annotation.JsonbDateFormat;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDTO {
    private String id;
    private String cardNumber;

    @JsonbDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime creationDate;

    private String expirationDate;
    private Double price;
    private List<ProductVO> products;
    private String userID;
    private String validationNumber;
    private OrderStatus status;

}
