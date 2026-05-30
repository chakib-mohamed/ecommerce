package the.chak.ecommerce.pricing.control;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
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

    private static final Logger LOG = Logger.getLogger(PricingService.class);

    private static final String RULES_RESOURCE = "the/chak/pricing/ApplySpecialOffers.drl";

    @Inject
    ApplyPromotionsService applyPromotionsService;

    private KieContainer kieContainer;

    @PostConstruct
    void init() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ks.getResources().newClassPathResource(RULES_RESOURCE));
        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        KieRepository kr = ks.getRepository();
        kieContainer = ks.newKieContainer(kr.getDefaultReleaseId());
        LOG.infof("Drools rules loaded resource=%s", RULES_RESOURCE);
    }

    public PriceCalculationResponse calculate(PriceCalculationRequest request) {
        OrderDTO order = request.getOrder();
        if (order == null || order.getProducts() == null || order.getProducts().isEmpty()) {
            throw new InvalidOrderException();
        }

        LOG.infof("Pricing calculation started products=%d", order.getProducts().size());

        applyPromotionsService.applyPromotion(order);
        applyDroolsRules(order);

        double total = order.getProducts().stream()
                .mapToDouble(p -> p.getPrice() * p.getQty())
                .sum();
        order.setPrice(Double.parseDouble(String.format(Locale.US, "%.2f", total)));

        String processId = UUID.randomUUID().toString();
        LOG.infof("Pricing calculation complete processId=%s total=%.2f", processId, order.getPrice());

        return new PriceCalculationResponse(processId, order);
    }

    private void applyDroolsRules(OrderDTO order) {
        StatelessKieSession session = kieContainer.newStatelessKieSession();
        session.execute(order);
    }
}
