package the.chak.ecommerce.orders.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import the.chak.ecommerce.orders.KafkaTestResource;
import the.chak.ecommerce.orders.MongoTestResource;
import the.chak.ecommerce.orders.RedisTestResource;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import the.chak.ecommerce.orders.repository.OrderRepository;

/**
 * Shipping and delivering an order.
 *
 * <p>These are the two transitions the lifecycle has always allowed and nothing could trigger. What
 * separates them from every other endpoint on this resource is who may call them: confirm and cancel
 * belong to the buyer and check ownership, while dispatching a parcel is a warehouse fact. The
 * permission being asked for here is a role, and owning the order is neither necessary nor
 * sufficient - which is why both directions of that are asserted below.
 *
 * <p>See docs/specs/order-fulfilment.md.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(RedisTestResource.class)
@Tag("integration")
class OrderFulfilmentResourceTest {

    private static final String BUYER = "buyer@ecommerce.test";

    @Inject
    OrderRepository orderRepository;

    @BeforeEach
    void cleanup() {
        orderRepository.deleteAll();
    }

    private Order orderIn(OrderStatus status) {
        Order order = new Order();
        order.setCreationDate(LocalDateTime.now().minusDays(1));
        order.setStatus(status);
        order.setUserID(BUYER);
        order.setPrice(BigDecimal.valueOf(100.0));
        order.setProducts(new ArrayList<>());
        orderRepository.persist(order);
        return order;
    }

    private OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.id).getStatus();
    }

    // --- ship ------------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Ships an order that has been paid for")
    void ship_paidOrder_transitionsToShipped() {
        // given
        Order order = orderIn(OrderStatus.PAID);

        // when
        given().when().post("/orders/" + order.id + "/ship")
                // then
                .then().statusCode(200).body("status", org.hamcrest.Matchers.is("SHIPPED"));

        assertEquals(OrderStatus.SHIPPED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "someone@ecommerce.test", roles = {"customer"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "someone@ecommerce.test")})
    @DisplayName("Refuses a caller who is not an administrator")
    void ship_nonAdmin_isForbidden() {
        // given
        Order order = orderIn(OrderStatus.PAID);

        // when / then
        given().when().post("/orders/" + order.id + "/ship").then().statusCode(403);

        assertEquals(OrderStatus.PAID, statusOf(order), "a refused call must change nothing");
    }

    @Test
    @TestSecurity(user = BUYER, roles = {"customer"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = BUYER)})
    @DisplayName("Refuses the buyer their own order, because owning it is not the permission asked for")
    void ship_theBuyersOwnOrder_isStillForbidden() {
        // given - this is the case most likely to be got wrong by copying confirm/cancel, which
        // pass exactly this check
        Order order = orderIn(OrderStatus.PAID);

        given().when().post("/orders/" + order.id + "/ship").then().statusCode(403);

        assertEquals(OrderStatus.PAID, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Ships an order belonging to somebody else, which is the entire point of the role")
    void ship_someoneElsesOrder_succeeds() {
        // given - the administrator owns no orders; if this checked ownership it would ship nothing
        Order order = orderIn(OrderStatus.PAID);

        given().when().post("/orders/" + order.id + "/ship").then().statusCode(200);

        assertEquals(OrderStatus.SHIPPED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Refuses to ship an order whose payment has not been taken")
    void ship_reservedOrder_isRejected() {
        // given - stock is held but no money has been taken; dispatching gives the goods away
        Order order = orderIn(OrderStatus.RESERVED);

        given().when().post("/orders/" + order.id + "/ship").then().statusCode(409);

        assertEquals(OrderStatus.RESERVED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Refuses to ship an order twice, rather than reporting a second dispatch that never happened")
    void ship_alreadyShipped_isRejected() {
        // given
        Order order = orderIn(OrderStatus.SHIPPED);

        // when / then - deliberately not idempotent: a 200 here would tell a retrying caller it had
        // just dispatched something that left the warehouse hours ago
        given().when().post("/orders/" + order.id + "/ship").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Refuses to ship a cancelled order")
    void ship_cancelledOrder_isRejected() {
        Order order = orderIn(OrderStatus.CANCELLED);

        given().when().post("/orders/" + order.id + "/ship").then().statusCode(409);

        assertEquals(OrderStatus.CANCELLED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Reports an order it cannot find")
    void ship_unknownOrder_isNotFound() {
        given().when().post("/orders/" + new ObjectId() + "/ship").then().statusCode(404);
    }

    // --- deliver ---------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Delivers an order that has shipped")
    void deliver_shippedOrder_transitionsToDelivered() {
        // given
        Order order = orderIn(OrderStatus.SHIPPED);

        given().when().post("/orders/" + order.id + "/deliver")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.is("DELIVERED"));

        assertEquals(OrderStatus.DELIVERED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Refuses to deliver an order that never shipped")
    void deliver_paidOrder_isRejected() {
        // given - PAID -> DELIVERED is not in the lifecycle's table, and skipping the dispatch would
        // leave an order that arrived without ever having left
        Order order = orderIn(OrderStatus.PAID);

        given().when().post("/orders/" + order.id + "/deliver").then().statusCode(409);

        assertEquals(OrderStatus.PAID, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Refuses to deliver an order twice, because delivered is final")
    void deliver_alreadyDelivered_isRejected() {
        Order order = orderIn(OrderStatus.DELIVERED);

        given().when().post("/orders/" + order.id + "/deliver").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "someone@ecommerce.test", roles = {"customer"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "someone@ecommerce.test")})
    @DisplayName("Refuses delivery to a caller who is not an administrator")
    void deliver_nonAdmin_isForbidden() {
        Order order = orderIn(OrderStatus.SHIPPED);

        given().when().post("/orders/" + order.id + "/deliver").then().statusCode(403);

        assertEquals(OrderStatus.SHIPPED, statusOf(order));
    }

    @Test
    @TestSecurity(user = "admin@ecommerce.test", roles = {"admin"})
    @JwtSecurity(claims = {@Claim(key = "sub", value = "admin@ecommerce.test")})
    @DisplayName("Reports a delivery for an order it cannot find")
    void deliver_unknownOrder_isNotFound() {
        given().when().post("/orders/" + new ObjectId() + "/deliver").then().statusCode(404);
    }
}
