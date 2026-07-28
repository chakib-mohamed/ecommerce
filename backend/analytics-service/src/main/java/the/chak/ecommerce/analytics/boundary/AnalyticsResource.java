package the.chak.ecommerce.analytics.boundary;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.security.Authenticated;
import the.chak.ecommerce.analytics.boundary.dto.AnalyticsResponse;
import the.chak.ecommerce.analytics.control.AnalyticsService;

/** The back-office dashboard's read endpoint. */
@Path("/analytics")
@Authenticated
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {

    @Inject
    AnalyticsService analyticsService;

    @GET
    public AnalyticsResponse getAnalytics() {
        return analyticsService.buildAnalytics();
    }
}
