package the.chak.ecommerce.orders.boundary;

import java.util.List;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/orders")
public interface OrdersApi {

    @POST
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    Tuple<Long, List<OrderDTO>> searchOrders(SearchOrdersCommand searchOrdersCommand);

    @POST
    Response createOrder(OrderRequest orderRequest);

    @PUT
    Response updateOrder(OrderRequest orderRequest);

    @DELETE
    @Path("/{orderID}")
    Response deleteOrder(@PathParam("orderID") String orderID);

    @POST
    @Path("/{orderID}/confirm")
    Response confirmOrder(@PathParam("orderID") String orderID);

}
