package the.chak.ecommerce.orders.boundary;

import the.chak.ecommerce.orders.boundary.dto.AddItemRequest;
import the.chak.ecommerce.orders.boundary.dto.UpdateItemRequest;
import the.chak.ecommerce.orders.boundary.dto.CartResponse;
import the.chak.ecommerce.orders.control.CartService;
import the.chak.ecommerce.orders.entity.Order;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Authenticated
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource {

    @Inject
    CartService cartService;

    @Inject
    OrderMapper orderMapper;

    @GET
    public Response getCart(@Context SecurityContext sec) {
        String userId = sec.getUserPrincipal().getName();
        return cartService.getCart(userId)
                .map(cart -> Response.ok(cart).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/items")
    public Response addItem(@Context SecurityContext sec, @Valid AddItemRequest request) {
        String userId = sec.getUserPrincipal().getName();
        CartResponse cart = cartService.addItem(userId, request);
        return Response.status(Response.Status.CREATED).entity(cart).build();
    }

    @PUT
    @Path("/items/{productId}")
    public Response updateItem(@Context SecurityContext sec, @PathParam("productId") String productId, @Valid UpdateItemRequest request) {
        String userId = sec.getUserPrincipal().getName();
        return cartService.updateItem(userId, productId, request)
                .map(cart -> Response.ok(cart).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/items/{productId}")
    public Response removeItem(@Context SecurityContext sec, @PathParam("productId") String productId) {
        String userId = sec.getUserPrincipal().getName();
        boolean removed = cartService.removeItem(userId, productId);
        return removed
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    @DELETE
    public Response clearCart(@Context SecurityContext sec) {
        String userId = sec.getUserPrincipal().getName();
        cartService.clearCart(userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/checkout")
    @Consumes(MediaType.WILDCARD)
    public Response checkout(@Context SecurityContext sec) {
        String userId = sec.getUserPrincipal().getName();
        Order order = cartService.checkout(userId);
        return Response.status(Response.Status.CREATED).entity(orderMapper.orderToOrderDto(order)).build();
    }
}
