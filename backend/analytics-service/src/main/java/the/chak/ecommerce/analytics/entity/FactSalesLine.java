package the.chak.ecommerce.analytics.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Sales fact: one row per line of a completed order.
 *
 * <p>Amounts are snapshots taken when the order was placed, so figures stay stable when a
 * product's live price later changes. {@code lineRevenue} is computed once at ingest rather than
 * at query time, which keeps the dashboard rollups to plain sums.
 *
 * <p>The {@code (orderId, productId)} uniqueness is what makes ingestion idempotent: the event
 * stream delivers at-least-once, so the same order can arrive more than once.
 */
@Entity
@Table(name = "fact_sales_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_fact_sales_line_order_product",
                columnNames = {"order_id", "product_id"}))
@Getter
@Setter
public class FactSalesLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    /** Product name as it was at purchase time; the dimension may since have been renamed. */
    private String productTitle;

    @Column(nullable = false)
    private Integer units;

    @Column(nullable = false)
    private Double unitPrice;

    /** Discount percentage that applied at purchase time, if any. */
    private Double percentageOff;

    @Column(nullable = false)
    private Double lineRevenue;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    /** {@code YYYY-MM} bucket, so the monthly rollup is a plain group-by. */
    @Column(name = "order_month", nullable = false)
    private String orderMonth;

    private String userId;
}
