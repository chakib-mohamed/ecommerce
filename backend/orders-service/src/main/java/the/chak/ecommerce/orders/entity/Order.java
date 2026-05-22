package the.chak.ecommerce.orders.entity;

import java.time.LocalDateTime;
import java.util.List;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MongoEntity(collection = "order")
public class Order extends PanacheMongoEntity {
    private LocalDateTime creationDate;
    private Double price;
    private List<ProductVO> products;
    private OrderStatus status;
    private String userID;
    private String validationNumber;
    private String processID;
}
