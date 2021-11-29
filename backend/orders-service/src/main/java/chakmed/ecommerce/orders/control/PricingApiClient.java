package chakmed.ecommerce.orders.control;

import chakmed.ecommerce.orders.control.command.PriceRequest;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@RegisterRestClient
@Path("/pricing")
public interface PricingApiClient {

    @LoggingFilter.Logged
    @Timeout(2000)
    @POST
    Response calculatePrice(PriceRequest order) ;

}