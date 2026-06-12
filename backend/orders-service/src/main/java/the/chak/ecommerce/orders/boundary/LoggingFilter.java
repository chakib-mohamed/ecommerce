package the.chak.ecommerce.orders.boundary;

import org.jboss.logging.Logger;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.Optional;

/**
 * Logs one INFO line per inbound HTTP request. Correlation is carried by {@code traceId}/{@code
 * spanId}, which OpenTelemetry loads into MDC automatically, so the log pattern renders them on
 * every line in the request thread. Health and metrics endpoints under {@code /q/} are suppressed.
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(LoggingFilter.class);

    @Context
    UriInfo info;

    @Override
    public void filter(ContainerRequestContext context) {
        String path = info.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.startsWith("/q/")) {
            return;
        }

        String userId = Optional.ofNullable(context.getSecurityContext())
                .map(SecurityContext::getUserPrincipal)
                .map(Principal::getName)
                .orElse("anonymous");

        LOG.infof("%s %s userId=%s", context.getMethod(), path, userId);
    }
}
