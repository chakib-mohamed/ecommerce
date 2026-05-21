package the.chak.ecommerce.pricing.control;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationRequest;
import the.chak.ecommerce.pricing.boundary.dto.PriceCalculationResponse;
import the.chak.ecommerce.pricing.control.exceptions.InvalidOrderException;

import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class PricingService {

    @Inject
    ApplyPromotionsService applyPromotionsService;

    private KieContainer kieContainer;

    @PostConstruct
    void init() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ks.getResources().newClassPathResource("the/chak/pricing/ApplySpecialOffers.drl"));
        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        KieRepository kr = ks.getRepository();
        kieContainer = ks.newKieContainer(kr.getDefaultReleaseId());
    }

    public PriceCalculationResponse calculate(PriceCalculationRequest request) {
        OrderDTO order = request.getOrder();
        if (order == null || order.getProducts() == null || order.getProducts().isEmpty()) {
            throw new InvalidOrderException();
        }

        applyPromotionsService.applyPromotion(order);
        applyDroolsRules(order);

        double total = order.getProducts().stream()
                .mapToDouble(p -> p.getPrice() * p.getQty())
                .sum();
        order.setPrice(Double.parseDouble(String.format(Locale.US, "%.2f", total)));

        return new PriceCalculationResponse(UUID.randomUUID().toString(), order);
    }

    private void applyDroolsRules(OrderDTO order) {
        StatelessKieSession session = kieContainer.newStatelessKieSession();
        session.execute(order);
    }
}
