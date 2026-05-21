package the.chak.ecommerce.pricing.control;

import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class ApplyPromotionsService {

    public OrderDTO applyPromotion(OrderDTO order) {
        Double price = order.getProducts().stream()
                .map(this::calculateNewPrice)
                .reduce(0D, Double::sum);

        order.setPrice(Double.parseDouble(String.format(Locale.US, "%.2f", price)));
        return order;
    }

    private Double calculateNewPrice(ProductVO productVO) {
        double discountedUnitPrice = Optional.ofNullable(productVO.getPercentageOff())
                .map(percentageOff -> productVO.getPrice() * (1 - percentageOff / 100.0))
                .orElse(productVO.getPrice());
        productVO.setPrice(discountedUnitPrice);
        return discountedUnitPrice * productVO.getQty();
    }
}
