package chakmed.ecommerce.orders.boundary;

import chakmed.ecommerce.orders.boundary.command.CreateOrderCommand;
import chakmed.ecommerce.orders.boundary.command.SearchOrdersCommand;
import chakmed.ecommerce.orders.control.OrderService;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;
import chakmed.ecommerce.orders.entity.Tuple;
import org.bson.types.ObjectId;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class OrdersResource implements OrdersApi {

    @Inject
    OrderMapper orderMapper;

    @Inject
    OrderService orderService;

    public Tuple<Integer, List<OrderDTO>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        Tuple<Integer, List<Order>> orders =  orderService.searchOrders(searchOrdersCommand);

        return new Tuple(orders.getX(), orders.getY().stream().map(o -> orderMapper.orderToOrderDto(o)).collect(toList()));
    }

    public Response createOrder(CreateOrderCommand createOrderCommand) {

        var order = orderMapper.toOrder(createOrderCommand);
        orderService.saveOrder(order);

        return Response.ok(order).status(201).build();
    }

    public Response deleteOrder(String orderID) {

        Order.deleteById(new ObjectId(orderID));

        return Response.ok().status(200).build();
    }

}