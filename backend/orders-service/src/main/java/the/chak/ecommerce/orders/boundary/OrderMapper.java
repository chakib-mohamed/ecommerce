package the.chak.ecommerce.orders.boundary;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.entity.Order;

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
