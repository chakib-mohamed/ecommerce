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
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import the.chak.ecommerce.orders.boundary.dto.SearchOrdersCommand;
import the.chak.ecommerce.orders.boundary.dto.Tuple;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.dto.PromotionDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    ProductsApiClient productsApiClient;

    @Inject
    @RestClient
    PricingApiClient pricingApiClient;

    @Inject
    the.chak.ecommerce.orders.repository.OrderRepository orderRepository;

    public Order saveOrder(Order order) {
        order.setCreationDate(LocalDateTime.now());
        order.setStatus(OrderStatus.INITIATED);

        order.getProducts().forEach(productVO -> {
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
        pricingOrder.setProducts(order.getProducts().stream().map(p -> {
            PricingOrder.PricingOrderProduct item = new PricingOrder.PricingOrderProduct();
            item.setProductID(p.getProductID());
            item.setQty(p.getQty());
            item.setPrice(p.getPrice());
            item.setPercentageOff(p.getPercentageOff());
            return item;
        }).toList());

        long start = System.currentTimeMillis();
        Response response = pricingApiClient.calculatePrice(pricingOrder);
        LOG.infof("POST pricing-service /pricing/calculate products=%d status=%d elapsed=%dms",
                pricingOrder.getProducts().size(), response.getStatus(),
                System.currentTimeMillis() - start);
        PricingResult result = response.readEntity(PricingResult.class);
        order.setPrice(result.getOrder().getPrice());
        order.setProcessID(result.getId());
        orderRepository.persist(order);
        LOG.infof("Order created orderId=%s userId=%s products=%d total=%.2f",
                order.getId(), order.getUserID(), order.getProducts().size(), order.getPrice());
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

        var panacheQuery = orderRepository.find(query, params);

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
        Order order = orderRepository.findById(new org.bson.types.ObjectId(orderId));
        if (order == null) {
            return null;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.persistOrUpdate(order);
        LOG.infof("Order confirmed orderId=%s userId=%s", order.getId(), order.getUserID());
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderRepository.findById(new org.bson.types.ObjectId(orderId)));
    }

    public void updateOrder(Order order) {
        orderRepository.persistOrUpdate(order);
    }

    public void deleteOrder(Order order) {
        orderRepository.delete(order);
        LOG.infof("Order deleted orderId=%s userId=%s", order.getId(), order.getUserID());
    }
}
