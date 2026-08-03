package the.chak.ecommerce.pricing.control;

import the.chak.ecommerce.orders.boundary.dto.Money;
import java.math.BigDecimal;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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

import java.util.ArrayList;
import java.util.UUID;

@ApplicationScoped
public class PricingService {

    private static final Logger LOG = Logger.getLogger(PricingService.class);

    private static final String RULES_RESOURCE = "the/chak/pricing/ApplySpecialOffers.drl";

    @Inject
    ApplyPromotionsService applyPromotionsService;

    @Inject
    MeterRegistry meterRegistry;

    private KieContainer kieContainer;

    /**
     * Build the rule base eagerly at boot rather than on first request. Compiling the KieBase and
     * warming a session takes a few seconds; deferring it to the first pricing call would blow the
     * caller's request timeout on a cold start. Runs at startup so the first real order is warm.
     */
    void init(@Observes StartupEvent ev) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ks.getResources().newClassPathResource(RULES_RESOURCE));
        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        KieRepository kr = ks.getRepository();
        kieContainer = ks.newKieContainer(kr.getDefaultReleaseId());
        warmUp();
        LOG.infof("Drools rules loaded resource=%s", RULES_RESOURCE);
    }

    /** Execute the rules once against an empty order to trigger session JIT compilation at boot. */
    private void warmUp() {
        OrderDTO warmUpOrder = new OrderDTO();
        warmUpOrder.setProducts(new ArrayList<>());
        applyDroolsRules(warmUpOrder);
    }

    public PriceCalculationResponse calculate(PriceCalculationRequest request) {
        OrderDTO order = request.getOrder();
        if (order == null || order.getProducts() == null || order.getProducts().isEmpty()) {
            recordCalculation(MetricNames.OUTCOME_FAILURE);
            throw new InvalidOrderException();
        }

        LOG.infof("Pricing calculation started products=%d", order.getProducts().size());

        applyPromotionsService.applyPromotion(order);
        applyDroolsRules(order);

        // The one place an order total is finalised. Every unit price is already at cent scale, so
        // this sum is exact; rounding it is a guard against a rule handing back a longer scale,
        // not a second rounding of an already-rounded figure.
        BigDecimal total = order.getProducts().stream()
                .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setPrice(Money.round(total));
        order.setCurrency(Money.DEFAULT_CURRENCY);

        String processId = UUID.randomUUID().toString();
        recordCalculation(MetricNames.OUTCOME_SUCCESS);
        LOG.infof("Pricing calculation complete processId=%s total=%s %s", processId,
                order.getPrice(), order.getCurrency());

        return new PriceCalculationResponse(processId, order);
    }

    private void recordCalculation(String outcome) {
        meterRegistry.counter(MetricNames.PRICING_CALCULATIONS, MetricNames.TAG_OUTCOME, outcome).increment();
    }

    private void applyDroolsRules(OrderDTO order) {
        StatelessKieSession session = kieContainer.newStatelessKieSession();
        session.execute(order);
    }
}
