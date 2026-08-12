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

    /**
     * Five seconds, matching the products client for the same reason: two is tighter than a first
     * call on a cold stack can meet, and both are on the path that prices an order before it is
     * written. Still bounded - pricing that hangs should fail the checkout rather than hold it.
     */
    @Timeout(5000)
    @POST
    Response calculatePrice(PricingRequest request);

}
