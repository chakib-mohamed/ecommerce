package the.chak.ecommerce.orders.control;

import the.chak.ecommerce.orders.boundary.dto.AddItemRequest;
import the.chak.ecommerce.orders.boundary.dto.UpdateItemRequest;
import the.chak.ecommerce.orders.boundary.dto.CartItemResponse;
import the.chak.ecommerce.orders.boundary.dto.CartResponse;
import the.chak.ecommerce.orders.entity.ProductVO;
import the.chak.ecommerce.orders.control.exceptions.CartEmptyException;
import the.chak.ecommerce.orders.control.exceptions.CartNotFoundException;
import the.chak.ecommerce.orders.entity.Cart;
import the.chak.ecommerce.orders.entity.CartItem;
import the.chak.ecommerce.orders.entity.Order;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CartService {

    private static final Logger LOG = Logger.getLogger(CartService.class);

    @Inject
    PriceCacheService priceCacheService;

    @Inject
    OrderService orderService;

    @Inject
    the.chak.ecommerce.orders.repository.CartRepository cartRepository;

    @Inject
    MeterRegistry meterRegistry;

    public CartResponse addItem(String userId, AddItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart c = new Cart();
            c.userId = userId;
            return c;
        });

        Optional<CartItem> existing = cart.items.stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + request.getQuantity());
        } else {
            cart.items.add(new CartItem(request.getProductId(), request.getQuantity()));
        }

        cart.updatedAt = Instant.now();
        cartRepository.persistOrUpdate(cart);

        LOG.infof("Cart item added userId=%s productId=%s qty=%d",
                userId, request.getProductId(), request.getQuantity());
        return toResponse(cart);
    }

    public Optional<CartResponse> getCart(String userId) {
        return cartRepository.findByUserId(userId).map(this::toResponse);
    }

    public Optional<CartResponse> updateItem(String userId, String productId, UpdateItemRequest request) {
        return cartRepository.findByUserId(userId).flatMap(cart -> {
            Optional<CartItem> item = cart.items.stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst();

            if (item.isEmpty()) {
                return Optional.empty();
            }

            item.get().setQuantity(request.getQuantity());
            cart.updatedAt = Instant.now();
            cartRepository.persistOrUpdate(cart);
            LOG.infof("Cart item updated userId=%s productId=%s newQty=%d",
                    userId, productId, request.getQuantity());
            return Optional.of(toResponse(cart));
        });
    }

    public boolean removeItem(String userId, String productId) {
        return cartRepository.findByUserId(userId).map(cart -> {
            boolean removed = cart.items.removeIf(i -> i.getProductId().equals(productId));
            if (removed) {
                cart.updatedAt = Instant.now();
                cartRepository.persistOrUpdate(cart);
                LOG.infof("Cart item removed userId=%s productId=%s", userId, productId);
            }
            return removed;
        }).orElse(false);
    }

    public boolean clearCart(String userId) {
        return cartRepository.findByUserId(userId).map(cart -> {
            cartRepository.delete(cart);
            LOG.infof("Cart cleared userId=%s", userId);
            return true;
        }).orElse(false);
    }

    public Order checkout(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    recordCheckout(MetricNames.OUTCOME_FAILURE);
                    return new CartNotFoundException(userId);
                });
        if (cart.items.isEmpty()) {
            recordCheckout(MetricNames.OUTCOME_FAILURE);
            throw new CartEmptyException();
        }

        LOG.infof("Checkout started userId=%s items=%d", userId, cart.items.size());

        List<ProductVO> products = cart.items.stream().map(i -> {
            ProductVO p = new ProductVO();
            p.setProductID(i.getProductId());
            p.setQty(i.getQuantity());
            return p;
        }).toList();

        Order order = new Order();
        order.setUserID(userId);
        order.setProducts(products);

        orderService.saveOrder(order);
        cartRepository.delete(cart);
        recordCheckout(MetricNames.OUTCOME_SUCCESS);
        LOG.infof("Cart cleared userId=%s", userId);
        return order;
    }

    private void recordCheckout(String outcome) {
        meterRegistry.counter(MetricNames.CHECKOUTS, MetricNames.TAG_OUTCOME, outcome).increment();
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.items.stream()
                .map(i -> {
                    Double unitPrice = priceCacheService.getPrice(i.getProductId());
                    Double totalPrice = unitPrice != null ? unitPrice * i.getQuantity() : null;
                    return new CartItemResponse(i.getProductId(), i.getQuantity(), unitPrice, totalPrice);
                })
                .toList();
        return new CartResponse(cart.userId, items, cart.updatedAt);
    }
}
