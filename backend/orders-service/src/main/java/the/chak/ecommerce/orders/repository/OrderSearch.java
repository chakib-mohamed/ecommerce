package the.chak.ecommerce.orders.repository;

/**
 * Neutral, repository-owned input for an order search: the optional owner filter plus paging
 * window. Keeps the persistence layer free of boundary DTOs - the control layer maps the inbound
 * search command onto this type before handing it to the repository. A {@code null} {@code userID}
 * means "no owner filter"; {@code offset}/{@code limit} are applied only when both are present.
 */
public record OrderSearch(String userID, Integer offset, Integer limit) {
}
