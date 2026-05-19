package the.chak.ecommerce.orders.control;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import the.chak.ecommerce.orders.boundary.OrderMapper;
import the.chak.ecommerce.orders.boundary.dto.OrderRequest;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.PriceRequest;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.OrderStatus;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.orders.entity.Order;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;

@ApplicationScoped
public class OrderService {

    @Inject
    ProductsApiClient productsApiClient;

    @Inject
    @RestClient
    PricingApiClient pricingApi;

    @Inject
    OrderMapper orderMapper;

    @Inject
    @Channel("order-initiated")
    Emitter<OrderDTO> orderEmitter;

    public Order saveOrder(Order order) {

        order.setCreationDate(LocalDateTime.now());
        order.setStatus(OrderStatus.INITIATED);

        order.getProducts().forEach(productVO -> {
            ProductDto product = productsApiClient.getProduct(productVO.getProductID());
            productVO.setTitle(product.getTitle());
            productVO.setPrice(product.getPrice());
            productVO.setPercentageOff(Optional.ofNullable(product.getPromotions())
                    .map(promos -> promos.stream().filter(this::isPromotionActive)
                            .collect(Collectors.toList()))
                    .map(promos -> promos.stream().map(PromotionDto::getPercentageOff).reduce(0d,
                            Double::sum))
                    .orElse(null));
        });

        OrderDTO orderDTO = orderMapper.orderToOrderDto(order);
        var response = pricingApi.calculatePrice(new PriceRequest(orderDTO))
                .readEntity(PriceRequest.class);

        order.setPrice(response.getOrder().getPrice());
        order.persist();
        order.setProcessID(response.getId());

        return order;
    }

    public Order updateOrder(String orderId, OrderRequest orderRequest) {
        Order order = Order.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        orderMapper.updateOrderFromRequest(orderRequest, order);
        order.update();
        return order;
    }

    private boolean isPromotionActive(PromotionDto promotion) {
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
            panacheQuery
                    .page(Page.of(searchOrdersCommand.getOffset(), searchOrdersCommand.getLimit()));
        }

        long totalCount = panacheQuery.count();
        int count = (int) totalCount;
        List<Order> result =
                panacheQuery.stream().map(Order.class::cast).collect(Collectors.toList());

        return new Tuple<>(count, result);
    }

    public Order confirmOrder(String orderId) {
        Order order = Order.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.update();

        OrderDTO orderDTO = orderMapper.orderToOrderDto(order);
        orderEmitter.send(orderDTO);

        return order;
    }
}
