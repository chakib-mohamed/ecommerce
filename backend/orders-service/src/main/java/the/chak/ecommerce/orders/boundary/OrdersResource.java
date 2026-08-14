package the.chak.ecommerce.orders.boundary;

import static java.util.stream.Collectors.toList;
import java.util.List;
import the.chak.ecommerce.orders.boundary.dto.ConfirmOrderRequest;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.orders.control.OrderService;
import the.chak.ecommerce.orders.entity.Order;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Authenticated
@RequestScoped
public class OrdersResource implements OrdersApi {

    /**
     * The role a fulfilment operator holds. Matches the {@code groups} claim authenticate-service
     * mints from the user's stored roles - the claim name is what {@code @RolesAllowed} reads, so
     * the two have to agree or every check here silently refuses everyone.
     */
    private static final String ADMIN = "admin";

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
        order.setUserID(sec.getUserPrincipal().getName());
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
        // Past INITIATED the order has been committed and its sale published; an edit here would
        // leave the read models describing something that no longer matches, with no event to
        // reconcile them. Cancelling remains available.
        orderService.assertMutable(order);
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
        orderService.assertMutable(existing);
        orderService.deleteOrder(existing);
        return Response.ok().status(200).build();
    }

    public Response confirmOrder(String orderID, ConfirmOrderRequest confirmOrderRequest) {
        var existing = orderService.findById(orderID);
        if (existing.isEmpty()) {
            return Response.status(404).build();
        }
        Order order = existing.get();
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(order.getUserID())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Order confirmed = orderService.confirmOrder(orderID,
                confirmOrderRequest == null ? null : confirmOrderRequest.getPaymentMethod());
        return Response.ok(confirmed).status(200).build();
    }

    public Response cancelOrder(String orderID) {
        var existing = orderService.findById(orderID);
        if (existing.isEmpty()) {
            return Response.status(404).build();
        }
        Order order = existing.get();
        String userId = sec.getUserPrincipal().getName();
        if (!userId.equals(order.getUserID())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Order cancelled = orderService.cancelOrder(orderID);
        return Response.ok(cancelled).status(200).build();
    }

    // Fulfilment. Note what is deliberately absent: the owner check every method above performs.
    // Dispatching a parcel is a warehouse fact, so the permission is the caller's role and owning
    // the order is neither necessary nor sufficient - an administrator ships orders belonging to
    // other people, which is the entire point, and a buyer may not ship their own.

    @RolesAllowed(ADMIN)
    public Response shipOrder(String orderID) {
        Order shipped = orderService.shipOrder(orderID);
        return shipped == null ? Response.status(404).build() : Response.ok(shipped).build();
    }

    @RolesAllowed(ADMIN)
    public Response deliverOrder(String orderID) {
        Order delivered = orderService.deliverOrder(orderID);
        return delivered == null ? Response.status(404).build() : Response.ok(delivered).build();
    }
}
