package chakmed.ecommerce.products.control;

import chakmed.ecommerce.products.control.events.ProductDataChangeEvent;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@Startup
@IfBuildProfile("dev")
public class InitService {

    @Inject
    Event<ProductDataChangeEvent> productDataChangeEvent;

    @Transactional
    @PostConstruct
    void init() {

        ProductMongoEntity.deleteAll();
        Product.find("from Product p left join fetch p.promotions").stream().map(Product.class::cast)
                .forEach(p -> productDataChangeEvent.fire(new ProductDataChangeEvent(p)));

    }

}
