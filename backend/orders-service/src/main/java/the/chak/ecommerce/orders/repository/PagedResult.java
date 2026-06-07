package the.chak.ecommerce.orders.repository;

import java.util.List;

/**
 * Neutral, repository-owned result of a paged query: the total number of matching records and the
 * page of items. Keeps the persistence layer free of boundary DTOs - the control layer maps this
 * onto the outbound response shape.
 */
public record PagedResult<T>(long total, List<T> items) {
}
