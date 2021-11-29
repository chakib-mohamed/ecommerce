package chakmed.ecommerce.orders.entity;

import io.quarkus.mongodb.panache.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import lombok.Data;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

@Data
@MongoEntity(collection = "order")
public class Order extends PanacheMongoEntity {
    private ObjectId id;
    private String cardNumber;
    private LocalDateTime creationDate;
    private String expirationDate;
    private Double price;
    private List<ProductVO> products;
    private OrderStatus status;
    private String userID;
    private String validationNumber;
    private String processID;

}
