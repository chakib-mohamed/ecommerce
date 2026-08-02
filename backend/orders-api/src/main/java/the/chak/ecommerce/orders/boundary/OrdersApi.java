package the.chak.ecommerce.orders.boundary;

import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;

// @RegisterClientHeaders activates the propagateHeaders config (Authorization) so a caller's
// bearer token rides along when another service uses this interface as a REST client (e.g.
// products-service verifying a reviewer's purchase history) - orders-service independently
// verifies it (zero-trust), matching PricingApiClient's convention.
@RegisterRestClient
@RegisterClientHeaders
@Path("/orders")
public interface OrdersApi {

    @POST
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    Tuple<Long, List<OrderDTO>> searchOrders(@Valid SearchOrdersCommand searchOrdersCommand);

    @POST
    Response createOrder(@Valid OrderRequest orderRequest);

    @PUT
    Response updateOrder(@Valid OrderRequest orderRequest);

    @DELETE
    @Path("/{orderID}")
    Response deleteOrder(@PathParam("orderID") String orderID);

    @POST
    @Path("/{orderID}/confirm")
    Response confirmOrder(@PathParam("orderID") String orderID);

    @POST
    @Path("/{orderID}/cancel")
    Response cancelOrder(@PathParam("orderID") String orderID);

}
