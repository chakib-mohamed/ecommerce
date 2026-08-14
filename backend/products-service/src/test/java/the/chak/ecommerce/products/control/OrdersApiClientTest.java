package the.chak.ecommerce.products.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import the.chak.ecommerce.orders.boundary.OrdersApi;
import the.chak.ecommerce.orders.boundary.dto.OrderStatus;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;

/**
 * What counts as having bought the thing you are reviewing.
 *
 * <p>The "Verified Purchase" badge is only worth anything if it cannot be minted on demand. Asking
 * whether the buyer has <em>an order</em> containing the product is not that test: placing an order
 * costs nothing, needs no payment, and can be abandoned immediately. Anyone could leave a verified
 * review on any product in the catalogue, for free, as many times as they liked.
 *
 * <p>So the question this client asks has to be about money having moved, and these tests are about
 * the question rather than the answer - {@link ReviewService} is where the answer is used, and its
 * tests mock this client out entirely, so nothing else in the suite can see what is being asked.
 */
class OrdersApiClientTest {

    private static final String REVIEWER = "buyer@ecommerce.test";
    private static final String PRODUCT_ID = "prod_under_review";

    private final OrdersApi ordersApi = mock(OrdersApi.class);

    private OrdersApiClient client() {
        OrdersApiClient client = new OrdersApiClient();
        client.ordersApi = ordersApi;
        return client;
    }

    @Test
    @DisplayName("Only counts orders that were paid for")
    void hasPurchased_asksOnlyForPaidOrders() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(0L, List.of()));

        // when
        client().hasPurchased(REVIEWER, PRODUCT_ID);

        // then
        ArgumentCaptor<SearchOrdersCommand> sent = ArgumentCaptor.forClass(SearchOrdersCommand.class);
        verify(ordersApi).searchOrders(sent.capture());
        List<OrderStatus> asked = sent.getValue().getStatuses();

        assertNotNull(asked,
                "no status filter was sent, so an order that was never paid for still counts as a "
                        + "purchase - which lets anyone review anything by placing an order and "
                        + "walking away");
        assertTrue(asked.containsAll(List.of(OrderStatus.PAID, OrderStatus.SHIPPED,
                        OrderStatus.DELIVERED)),
                "a paid, shipped or delivered order is a purchase and must count");
    }

    @Test
    @DisplayName("Does not count an order that has not been paid for")
    void hasPurchased_doesNotCountUnpaidStates() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(0L, List.of()));

        // when
        client().hasPurchased(REVIEWER, PRODUCT_ID);

        // then - each of these is an order the buyer can create and abandon at no cost, or one the
        // platform itself ended
        ArgumentCaptor<SearchOrdersCommand> sent = ArgumentCaptor.forClass(SearchOrdersCommand.class);
        verify(ordersApi).searchOrders(sent.capture());
        List<OrderStatus> asked = sent.getValue().getStatuses();

        assertNotNull(asked, "no status filter was sent");
        for (OrderStatus notAPurchase : List.of(OrderStatus.INITIATED, OrderStatus.CONFIRMED,
                OrderStatus.RESERVED, OrderStatus.CANCELLED)) {
            assertFalse(asked.contains(notAPurchase),
                    notAPurchase + " is not a purchase: no money has been taken and kept");
        }
    }

    @Test
    @DisplayName("Does not count a refunded order")
    void hasPurchased_doesNotCountRefunded() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(0L, List.of()));

        // when
        client().hasPurchased(REVIEWER, PRODUCT_ID);

        // then - the buyer has been made whole, so they no longer hold a purchase. This is the
        // judgement call in docs/specs/product-reviews.md, pinned here so changing it is deliberate.
        ArgumentCaptor<SearchOrdersCommand> sent = ArgumentCaptor.forClass(SearchOrdersCommand.class);
        verify(ordersApi).searchOrders(sent.capture());

        assertFalse(sent.getValue().getStatuses().contains(OrderStatus.REFUNDED),
                "a refunded order was returned to the buyer and is not a purchase");
    }

    @Test
    @DisplayName("Still scopes the question to this reviewer and this product")
    void hasPurchased_keepsTheExistingFilters() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(0L, List.of()));

        // when
        client().hasPurchased(REVIEWER, PRODUCT_ID);

        // then - adding a filter must not quietly drop the two that were already there
        ArgumentCaptor<SearchOrdersCommand> sent = ArgumentCaptor.forClass(SearchOrdersCommand.class);
        verify(ordersApi).searchOrders(sent.capture());

        assertTrue(REVIEWER.equals(sent.getValue().getUserID()), "the reviewer filter was lost");
        assertTrue(PRODUCT_ID.equals(sent.getValue().getProductID()), "the product filter was lost");
    }

    @Test
    @DisplayName("Reports a purchase when a paid order is found")
    void hasPurchased_matchFound_isTrue() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(1L, List.of()));

        // when / then
        assertTrue(client().hasPurchased(REVIEWER, PRODUCT_ID));
    }

    @Test
    @DisplayName("Reports no purchase when nothing is found")
    void hasPurchased_noMatch_isFalse() {
        // given
        when(ordersApi.searchOrders(any())).thenReturn(new Tuple<>(0L, List.of()));

        // when / then
        assertFalse(client().hasPurchased(REVIEWER, PRODUCT_ID));
    }
}
