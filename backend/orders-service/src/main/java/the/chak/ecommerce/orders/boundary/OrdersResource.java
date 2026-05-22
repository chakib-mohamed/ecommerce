package the.chak.ecommerce.orders.boundary;

import static java.util.stream.Collectors.toList;
import java.util.List;
import org.bson.types.ObjectId;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.orders.control.OrderService;
import the.chak.ecommerce.orders.entity.Order;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Authenticated
@RequestScoped
public class OrdersResource implements OrdersApi {

    @Inject
    OrderMapper orderMapper;

    @Inject
    OrderService orderService;

    @Inject
    @Channel("order-initiated")
    Emitter<OrderDTO> orderEmitter;

    @Context
    SecurityContext sec;

    public Tuple<Long, List<OrderDTO>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        Tuple<Long, List<Order>> orders = orderService.searchOrders(searchOrdersCommand);

        return new Tuple<>(orders.getX(),
                orders.getY().stream().map(orderMapper::orderToOrderDto).collect(toList()));
    }

    public Response createOrder(OrderRequest orderRequest) {
        Order order = orderMapper.toOrder(orderRequest);
        orderService.saveOrder(order);
        return Response.ok(order).status(201).build();
    }

    public Response updateOrder(OrderRequest orderRequest) {
        Order existing = Order.findById(new ObjectId(orderRequest.getId()));
        if (existing == null) {
            return Response.status(404).build();
        }
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(existing.userID)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        orderMapper.updateOrderFromRequest(orderRequest, existing);
        existing.update();
        return Response.ok(existing).status(200).build();
    }

    public Response deleteOrder(String orderID) {
        Order order = Order.findById(new ObjectId(orderID));
        if (order == null) {
            return Response.status(404).build();
        }
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(order.userID)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        order.delete();
        return Response.ok().status(200).build();
    }

    public Response confirmOrder(String orderID) {
        Order existing = Order.findById(new ObjectId(orderID));
        if (existing == null) {
            return Response.status(404).build();
        }
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(existing.userID)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Order order = orderService.confirmOrder(orderID);
        OrderDTO orderDTO = orderMapper.orderToOrderDto(order);
        orderEmitter.send(orderDTO);
        return Response.ok(order).status(200).build();
    }
}
