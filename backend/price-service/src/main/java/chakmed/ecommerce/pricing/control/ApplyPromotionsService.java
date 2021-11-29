package chakmed.ecommerce.pricing.control;

import chakmed.ecommerce.orders.entity.OrderDTO;

import javax.enterprise.context.ApplicationScoped;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Optional;

@ApplicationScoped
public class ApplyPromotionsService {

    public OrderDTO applyPromotion(OrderDTO order) {

        Double price = order.getProducts().stream().map(
                this::calculateNewPrice
        ).reduce(0D, (a, b) -> a + b);

        NumberFormat formatter = new DecimalFormat("#0.00");
        order.setPrice(Double.valueOf(formatter.format(price)));

        return order;
    }

    private Double calculateNewPrice(chakmed.ecommerce.orders.entity.ProductVO productVO) {
        Double productPrice = Optional.ofNullable(productVO.getPercentageOff())
        .map(percentageOff -> productVO.getQty() *  (productVO.getPrice() * (1 - percentageOff / 100)))
        .orElse(productVO.getPrice() * productVO.getQty());
        productVO.setPrice(productPrice);

        return productPrice;
    }
}