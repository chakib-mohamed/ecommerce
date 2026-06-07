package the.chak.ecommerce.orders.boundary;

import static java.util.stream.Collectors.toList;
import java.util.List;
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

@Authenticated
@RequestScoped
public class OrdersResource implements OrdersApi {

    @Inject
    OrderMapper orderMapper;

    @Inject
    OrderService orderService;

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
        var existing = orderService.findById(orderRequest.getId());
        if (existing.isEmpty()) {
            return Response.status(404).build();
        }
        Order order = existing.get();
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(order.getUserID())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        orderMapper.updateOrderFromRequest(orderRequest, order);
        orderService.updateOrder(order);
        return Response.ok(order).status(200).build();
    }

    public Response deleteOrder(String orderID) {
        var order = orderService.findById(orderID);
        if (order.isEmpty()) {
            return Response.status(404).build();
        }
        Order existing = order.get();
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(existing.getUserID())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        orderService.deleteOrder(existing);
        return Response.ok().status(200).build();
    }

    public Response confirmOrder(String orderID) {
        var existing = orderService.findById(orderID);
        if (existing.isEmpty()) {
            return Response.status(404).build();
        }
        Order order = existing.get();
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(order.getUserID())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Order confirmed = orderService.confirmOrder(orderID);
        return Response.ok(confirmed).status(200).build();
    }
}
