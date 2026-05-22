package the.chak.ecommerce.orders.entity;

import java.time.LocalDateTime;
import java.util.List;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "order")
public class Order extends PanacheMongoEntity {
    public LocalDateTime creationDate;
    public Double price;
    public List<ProductVO> products;
    public OrderStatus status;
    public String userID;
    public String validationNumber;
    public String processID;
}
