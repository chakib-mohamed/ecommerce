package the.chak.ecommerce.orders.control;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import the.chak.ecommerce.orders.control.exceptions.ProductNotFoundException;
import the.chak.ecommerce.orders.entity.Order;
import the.chak.ecommerce.orders.entity.OrderStatus;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrderService {

    @Inject
    ProductsApiClient productsApiClient;

    @Inject
    @RestClient
    PricingApiClient pricingApiClient;

    public Order saveOrder(Order order) {
        order.creationDate = LocalDateTime.now();
        order.status = OrderStatus.INITIATED;

        order.products.forEach(productVO -> {
            ProductDto product = productsApiClient.getProduct(productVO.getProductID());
            if (product == null) {
                throw new ProductNotFoundException(productVO.getProductID());
            }
            productVO.setTitle(product.getTitle());
            productVO.setPrice(product.getPrice());
            productVO.setPercentageOff(Optional.ofNullable(product.getPromotions())
                    .map(promos -> promos.stream().filter(this::isPromotionActive)
                            .collect(Collectors.toList()))
                    .map(promos -> promos.stream().map(PromotionDto::getPercentageOff).reduce(0d,
                            Double::sum))
                    .orElse(null));
        });

        PricingOrder pricingOrder = new PricingOrder();
        pricingOrder.setProducts(order.products.stream().map(p -> {
            PricingOrder.PricingOrderProduct item = new PricingOrder.PricingOrderProduct();
            item.setProductID(p.getProductID());
            item.setQty(p.getQty());
            item.setPrice(p.getPrice());
            item.setPercentageOff(p.getPercentageOff());
            return item;
        }).toList());

        PricingResult result = pricingApiClient.calculatePrice(pricingOrder).readEntity(PricingResult.class);
        order.price = result.getOrder().getPrice();
        order.processID = result.getId();
        order.persist();
        return order;
    }

    private boolean isPromotionActive(PromotionDto promotion) {
        if (promotion.getActiveFrom() == null || promotion.getActiveTo() == null) {
            return false;
        }
        var now = LocalDate.now();
        return promotion.getActiveFrom().isBefore(now) && now.isBefore(promotion.getActiveTo());
    }

    public Tuple<Long, List<Order>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        var query = "";
        Map<String, Object> params = new HashMap<>();
        if (searchOrdersCommand.getUserID() != null && !searchOrdersCommand.getUserID().isEmpty()) {
            query += "userID = :userID ";
            params.put("userID", searchOrdersCommand.getUserID());
        }

        var panacheQuery = Order.find(query, params);

        if (searchOrdersCommand.getLimit() != null && searchOrdersCommand.getOffset() != null) {
            panacheQuery
                    .page(Page.of(searchOrdersCommand.getOffset(), searchOrdersCommand.getLimit()));
        }

        long totalCount = panacheQuery.count();
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new Tuple<>(totalCount, result);
    }

    public Order confirmOrder(String orderId) {
        Order order = Order.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        order.status = OrderStatus.CONFIRMED;
        order.update();
        return order;
    }
}
