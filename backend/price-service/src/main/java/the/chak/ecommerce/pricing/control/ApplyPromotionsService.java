package the.chak.ecommerce.pricing.control;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import the.chak.ecommerce.orders.boundary.dto.Money;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Optional;

@ApplicationScoped
public class ApplyPromotionsService {

    @Inject
    MeterRegistry meterRegistry;

    /**
     * Applies each line's discount to its unit price.
     *
     * <p>Deliberately does not set the order total. Drools runs after this and may change unit
     * prices again, so a total computed here would be discarded - which is exactly what used to
     * happen, and it meant the surviving total was derived from unrounded unit prices. The total is
     * computed once, in {@link PricingService}, after every rule has had its say.
     */
    public OrderDTO applyPromotion(OrderDTO order) {
        order.getProducts().forEach(this::applyDiscount);
        return order;
    }

    private void applyDiscount(ProductVO productVO) {
        Double percentageOff = productVO.getPercentageOff();
        BigDecimal originalUnitPrice = productVO.getPrice();

        // Rounded here because the discounted unit price is itself a published amount: it goes back
        // on the order line and the buyer sees it. The line total is then an exact multiple of it.
        BigDecimal discountedUnitPrice = Optional.ofNullable(percentageOff)
                // movePointLeft rather than divide(100): exact by construction, so it cannot throw
                // on a non-terminating quotient the way divide without a scale can.
                .map(pct -> originalUnitPrice.multiply(
                        BigDecimal.ONE.subtract(BigDecimal.valueOf(pct).movePointLeft(2))))
                .map(Money::round)
                .orElse(originalUnitPrice);

        if (percentageOff != null) {
            recordDiscount(originalUnitPrice.subtract(discountedUnitPrice)
                    .multiply(BigDecimal.valueOf(productVO.getQty())));
        }
        productVO.setPrice(discountedUnitPrice);
    }

    private void recordDiscount(BigDecimal amount) {
        DistributionSummary.builder(MetricNames.PRICING_DISCOUNT_AMOUNT)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(amount.doubleValue());
    }
}
