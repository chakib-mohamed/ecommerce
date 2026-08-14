package the.chak.ecommerce.orders.repository;

/**
 * Neutral, repository-owned input for an order search: the optional owner, product and state
 * filters plus paging window. Keeps the persistence layer free of boundary DTOs - the control layer
 * maps the inbound search command onto this type before handing it to the repository. A
 * {@code null} {@code userID}/{@code productID} means "no filter on that field", as does a
 * {@code null} or empty {@code statuses}; {@code offset}/{@code limit} are applied only when both
 * are present.
 */
public record OrderSearch(String userID, String productID,
        java.util.List<the.chak.ecommerce.orders.entity.OrderStatus> statuses,
        Integer offset, Integer limit) {
}
