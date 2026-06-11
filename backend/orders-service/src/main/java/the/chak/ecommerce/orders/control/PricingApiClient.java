package the.chak.ecommerce.orders.control;

import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

// @RegisterClientHeaders activates the propagateHeaders config (Authorization) so the caller's
// bearer token rides along to price-service, which independently verifies it (zero-trust).
@RegisterRestClient
@RegisterClientHeaders
@Path("/pricing/calculate")
public interface PricingApiClient {

    @Timeout(2000)
    @POST
    Response calculatePrice(PricingRequest request);

}
