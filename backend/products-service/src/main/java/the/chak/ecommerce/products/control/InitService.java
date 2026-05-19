package the.chak.ecommerce.products.control;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.Product;

@Startup
@IfBuildProfile("dev")
public class InitService {

    @Inject
    Event<ProductUpdatedEvent> productDataChangeEvent;

    @Inject
    ProductMapper productMapper;

    @Transactional
    @PostConstruct
    void init() {
        Product.<Product>find("from Product p left join fetch p.promotions").list().forEach(
                p -> productDataChangeEvent.fire(new ProductUpdatedEvent(productMapper.toDto(p))));

    }

}
