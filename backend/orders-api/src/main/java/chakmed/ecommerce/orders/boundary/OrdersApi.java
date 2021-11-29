package chakmed.ecommerce.orders.boundary;

import chakmed.ecommerce.orders.boundary.command.CreateOrderCommand;
import chakmed.ecommerce.orders.boundary.command.SearchOrdersCommand;
import chakmed.ecommerce.orders.entity.OrderDTO;
import chakmed.ecommerce.orders.entity.Tuple;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/orders")
public interface OrdersApi {

    @POST
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    Tuple<Integer, List<OrderDTO>> searchOrders(SearchOrdersCommand searchOrdersCommand);

    @POST
    Response createOrder(CreateOrderCommand createOrderCommand);

    @DELETE
    @Path("/{orderID}")
    Response deleteOrder(@PathParam("orderID") String orderID);

}