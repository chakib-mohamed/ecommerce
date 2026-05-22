package the.chak.ecommerce.orders.control;

import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@RegisterRestClient
@Path("/pricing/calculate")
public interface PricingApiClient {

    @Timeout(2000)
    @POST
    Response calculatePrice(PricingOrder order);

}
