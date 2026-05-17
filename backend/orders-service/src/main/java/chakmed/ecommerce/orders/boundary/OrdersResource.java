package chakmed.ecommerce.orders.boundary;

import static java.util.stream.Collectors.toList;
import java.util.List;
import org.bson.types.ObjectId;
import chakmed.ecommerce.orders.boundary.command.OrderRequest;
import chakmed.ecommerce.orders.boundary.command.SearchOrdersCommand;
import chakmed.ecommerce.orders.control.OrderService;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;
import chakmed.ecommerce.orders.entity.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

public class OrdersResource implements OrdersApi {

    @Inject
    OrderMapper orderMapper;

    @Inject
    OrderService orderService;

    public Tuple<Integer, List<OrderDTO>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        Tuple<Integer, List<Order>> orders = orderService.searchOrders(searchOrdersCommand);

        return new Tuple<>(orders.getX(),
                orders.getY().stream().map(o -> orderMapper.orderToOrderDto(o)).collect(toList()));
    }

    public Response createOrder(OrderRequest orderRequest) {

        var order = orderMapper.toOrder(orderRequest);
        orderService.saveOrder(order);

        return Response.ok(order).status(201).build();
    }

    public Response updateOrder(OrderRequest orderRequest) {
        Order order = orderService.updateOrder(orderRequest.getId(), orderRequest);
        if (order == null) {
            return Response.status(404).build();
        }
        return Response.ok(order).status(200).build();
    }

    public Response deleteOrder(String orderID) {

        Order.deleteById(new ObjectId(orderID));

        return Response.ok().status(200).build();
    }

    public Response confirmOrder(String orderID) {
        Order order = orderService.confirmOrder(orderID);
        if (order == null) {
            return Response.status(404).build();
        }
        return Response.ok(order).status(200).build();
    }

}
