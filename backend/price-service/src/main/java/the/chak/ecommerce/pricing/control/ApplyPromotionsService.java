package the.chak.ecommerce.pricing.control;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class ApplyPromotionsService {

    @Inject
    MeterRegistry meterRegistry;

    public OrderDTO applyPromotion(OrderDTO order) {
        Double price = order.getProducts().stream()
                .map(this::calculateNewPrice)
                .reduce(0D, Double::sum);

        order.setPrice(Double.parseDouble(String.format(Locale.US, "%.2f", price)));
        return order;
    }

    private Double calculateNewPrice(ProductVO productVO) {
        Double percentageOff = productVO.getPercentageOff();
        double originalUnitPrice = productVO.getPrice();
        double discountedUnitPrice = Optional.ofNullable(percentageOff)
                .map(pct -> originalUnitPrice * (1 - pct / 100.0))
                .orElse(originalUnitPrice);
        if (percentageOff != null) {
            recordDiscount((originalUnitPrice - discountedUnitPrice) * productVO.getQty());
        }
        productVO.setPrice(discountedUnitPrice);
        return discountedUnitPrice * productVO.getQty();
    }

    private void recordDiscount(double amount) {
        DistributionSummary.builder(MetricNames.PRICING_DISCOUNT_AMOUNT)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(amount);
    }
}
