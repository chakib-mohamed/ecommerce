package the.chak.ecommerce.products.control;

/**
 * One product and how many of it an order wants held.
 *
 * @param productId the product's uuid, as order lines carry it
 * @param quantity  units to hold; always positive
 */
public record StockLine(String productId, int quantity) {
}
