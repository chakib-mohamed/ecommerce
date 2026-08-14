package the.chak.ecommerce.orders.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static the.chak.ecommerce.orders.entity.OrderStatus.CANCELLED;
import static the.chak.ecommerce.orders.entity.OrderStatus.CONFIRMED;
import static the.chak.ecommerce.orders.entity.OrderStatus.DELIVERED;
import static the.chak.ecommerce.orders.entity.OrderStatus.INITIATED;
import static the.chak.ecommerce.orders.entity.OrderStatus.PAID;
import static the.chak.ecommerce.orders.entity.OrderStatus.REFUNDED;
import static the.chak.ecommerce.orders.entity.OrderStatus.RESERVED;
import static the.chak.ecommerce.orders.entity.OrderStatus.SHIPPED;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import the.chak.ecommerce.orders.control.exceptions.IllegalOrderTransitionException;
import the.chak.ecommerce.orders.entity.OrderStatus;

/**
 * The transition table from docs/specs/order-lifecycle.md, asserted in both directions: every
 * legal move is allowed, and every move absent from the table is rejected.
 *
 * <p>The illegal cases matter more than the legal ones. A permissive state machine is how an
 * order gets confirmed twice, or edited after it was already priced and published.
 */
class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    /** The complete set of legal moves. Anything not listed here must be refused. */
    static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(INITIATED, CONFIRMED),
                Arguments.of(INITIATED, CANCELLED),
                Arguments.of(CONFIRMED, RESERVED),
                Arguments.of(CONFIRMED, CANCELLED),
                Arguments.of(RESERVED, PAID),
                Arguments.of(RESERVED, CANCELLED),
                Arguments.of(PAID, SHIPPED),
                Arguments.of(PAID, REFUNDED),
                Arguments.of(SHIPPED, DELIVERED),
                Arguments.of(SHIPPED, REFUNDED));
    }

    static Stream<Arguments> illegalTransitions() {
        Set<String> legal = legalTransitions()
                .map(a -> a.get()[0] + "->" + a.get()[1])
                .collect(java.util.stream.Collectors.toSet());
        return Stream.of(OrderStatus.values())
                .flatMap(from -> Stream.of(OrderStatus.values())
                        .filter(to -> !legal.contains(from + "->" + to))
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("legalTransitions")
    @DisplayName("Allows every transition the lifecycle defines")
    void allowsLegalTransitions(OrderStatus from, OrderStatus to) {
        assertTrue(stateMachine.canTransition(from, to),
                from + " -> " + to + " is in the spec's table and must be allowed");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("illegalTransitions")
    @DisplayName("Refuses every transition the lifecycle does not define")
    void refusesIllegalTransitions(OrderStatus from, OrderStatus to) {
        assertFalse(stateMachine.canTransition(from, to),
                from + " -> " + to + " is absent from the spec's table and must be refused");
    }

    @Test
    @DisplayName("Refuses to confirm an order that is already confirmed")
    void refusesDoubleConfirmation() {
        assertFalse(stateMachine.canTransition(CONFIRMED, CONFIRMED));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("Refuses a transition from a state to itself")
    void refusesSelfTransition(OrderStatus status) {
        assertFalse(stateMachine.canTransition(status, status));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "REFUNDED"})
    @DisplayName("Refuses to move an order out of a terminal state")
    void refusesLeavingTerminalStates(OrderStatus terminal) {
        for (OrderStatus to : OrderStatus.values()) {
            assertFalse(stateMachine.canTransition(terminal, to),
                    terminal + " is terminal, so " + terminal + " -> " + to + " must be refused");
        }
    }

    @Test
    @DisplayName("Reports the offending states when it rejects a transition")
    void namesBothStatesWhenRejecting() {
        IllegalOrderTransitionException thrown = assertThrows(IllegalOrderTransitionException.class,
                () -> stateMachine.assertCanTransition(CONFIRMED, CONFIRMED));
        assertTrue(thrown.getMessage().contains("CONFIRMED"),
                "the message should say which states were involved");
    }

    @Test
    @DisplayName("Rejects an illegal transition as a conflict, not a bad request")
    void rejectsIllegalTransitionAsConflict() {
        IllegalOrderTransitionException thrown = assertThrows(IllegalOrderTransitionException.class,
                () -> stateMachine.assertCanTransition(SHIPPED, INITIATED));
        assertTrue(thrown.getStatus().getStatusCode() == 409,
                "an order in the wrong state is a conflict");
    }

    @Test
    @DisplayName("Accepts a legal transition without complaint")
    void acceptsLegalTransition() {
        stateMachine.assertCanTransition(INITIATED, CONFIRMED);
    }

    @Test
    @DisplayName("Treats a newly initiated order as still changeable")
    void treatsInitiatedAsMutable() {
        assertTrue(stateMachine.isMutable(INITIATED));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "INITIATED")
    @DisplayName("Treats an order past initiation as no longer changeable")
    void treatsEverythingElseAsImmutable(OrderStatus status) {
        assertFalse(stateMachine.isMutable(status),
                status + " is past INITIATED, so the order must be frozen");
    }
}
