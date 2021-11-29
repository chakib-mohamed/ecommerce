package chakmed.ecommerce.orders.boundary;

import chakmed.ecommerce.orders.boundary.command.CreateOrderCommand;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;

import java.util.Optional;

@Mapper(componentModel = "cdi")
public interface OrderMapper {
    Order toOrder(CreateOrderCommand createOrderCommand);

    OrderDTO orderToOrderDto(Order order);

    default  ObjectId orderIDtoObjectID(String orderID) {
        return orderID != null ? new ObjectId(orderID) : null;
    }

    default  String objectIDToOrderID(ObjectId objectId) {
        return Optional.ofNullable(objectId).map(ObjectId::toString).orElse(null);
    }
}
