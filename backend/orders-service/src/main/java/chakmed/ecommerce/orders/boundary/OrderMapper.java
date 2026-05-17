package chakmed.ecommerce.orders.boundary;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import chakmed.ecommerce.orders.boundary.command.OrderRequest;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;

@Mapper(componentModel = "jakarta")
public interface OrderMapper {
    Order toOrder(OrderRequest orderRequest);

    @org.mapstruct.Mapping(target = "id", ignore = true)
    void updateOrderFromRequest(OrderRequest orderRequest, @MappingTarget Order order);

    OrderDTO orderToOrderDto(Order order);

    default ObjectId orderIDtoObjectID(String orderID) {
        return orderID != null ? new ObjectId(orderID) : null;
    }

    default String objectIDToOrderID(ObjectId objectId) {
        return Optional.ofNullable(objectId).map(ObjectId::toString).orElse(null);
    }
}
