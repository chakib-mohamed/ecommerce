package chakmed.ecommerce.orders.control;

import chakmed.ecommerce.orders.boundary.OrderMapper;
import chakmed.ecommerce.orders.boundary.command.SearchOrdersCommand;
import chakmed.ecommerce.orders.control.command.PriceRequest;
import chakmed.ecommerce.orders.entity.Order;
import chakmed.ecommerce.orders.entity.OrderDTO;
import chakmed.ecommerce.orders.entity.OrderStatus;
import chakmed.ecommerce.orders.entity.Tuple;
import chakmed.ecommerce.products.entity.ProductDTO;
import chakmed.ecommerce.products.entity.PromotionDTO;
import io.quarkus.panache.common.Page;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrderService {

    @Inject
    ProductsApiClient productsApiClient;

    @Inject
    @RestClient
    PricingApiClient pricingApi;

    @Inject
    OrderMapper orderMapper;

    public Order saveOrder(Order order) {

        order.setCreationDate(LocalDateTime.now());
        order.setStatus(OrderStatus.INITIATED);

        order.getProducts().forEach(
                productVO -> {
                    ProductDTO product = productsApiClient.getProduct(productVO.getProductID());
                    productVO.setTitle(product.getTitle());
                    productVO.setPrice(product.getPrice());
                    productVO.setPercentageOff(Optional.ofNullable(product.getPromotions())
                            .map(promos -> promos.stream().filter(this::isPromotionActive).collect(Collectors.toList()))
                            .map(promos -> promos.stream().map(PromotionDTO::getPercentageOff).reduce(0d, Double::sum))
                            .orElse(null));
                }
        );

        OrderDTO orderDTO = orderMapper.orderToOrderDto(order);
        var response = pricingApi.calculatePrice(new PriceRequest(orderDTO)).readEntity(PriceRequest.class);

        order.setPrice(response.getOrder().getPrice());
        order.persist();
        order.setProcessID(response.getId());

        return order;
    }

    private boolean isPromotionActive(PromotionDTO promotion) {
        var now = LocalDate.now();
        return promotion.getActiveFrom().isBefore(now) && now.isBefore(promotion.getActiveTo());
    }


    public Tuple<Integer, List<Order>> searchOrders(SearchOrdersCommand searchOrdersCommand) {
        var query = "";
        Map<String, Object> params = new HashMap<>();
        if (searchOrdersCommand.getUserID() != null && !searchOrdersCommand.getUserID().isEmpty()) {
            query += "userID = :userID ";
            params.put("userID", searchOrdersCommand.getUserID());
        }

        var panacheQuery = Order.find(query, params);

        if (searchOrdersCommand.getLimit() != null && searchOrdersCommand.getOffset() != null) {
            panacheQuery.page(Page.of(searchOrdersCommand.getOffset(), searchOrdersCommand.getLimit()));
        }

        var count = panacheQuery.pageCount();
        List<Order> result = panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new Tuple(count, result);
    }
}
