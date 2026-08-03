package the.chak.ecommerce.products.repository;

import java.util.List;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.products.entity.ReservationStatus;
import the.chak.ecommerce.products.entity.StockReservation;

@ApplicationScoped
public class StockReservationRepository implements PanacheRepository<StockReservation> {

    public List<StockReservation> findByOrderId(String orderId) {
        return list("orderId", orderId);
    }

    public List<StockReservation> findByOrderIdAndStatus(String orderId, ReservationStatus status) {
        return list("orderId = ?1 and status = ?2", orderId, status);
    }
}
