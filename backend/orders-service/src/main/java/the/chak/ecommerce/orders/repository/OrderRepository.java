package the.chak.ecommerce.orders.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.orders.entity.Order;

@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {
}
