package the.chak.ecommerce.authentication.boundary;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Logs one INFO line per inbound HTTP request and loads the correlation id into MDC
 * so every subsequent log in the request thread carries {@code requestId} automatically.
 * Health and metrics endpoints under {@code /q/} are suppressed; MDC is cleared on the
 * response to avoid leaks across pooled threads.
 */
@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class);

    @Context
    UriInfo info;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = info.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.startsWith("/q/")) {
            return;
        }

        String requestId = Optional.ofNullable(ctx.getHeaderString("X-Request-ID"))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("requestId", requestId);

        String userId = Optional.ofNullable(ctx.getSecurityContext())
                .map(SecurityContext::getUserPrincipal)
                .map(Principal::getName)
                .orElse("anonymous");

        LOG.infof("%s %s userId=%s requestId=%s", ctx.getMethod(), path, userId, requestId);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        MDC.remove("requestId");
    }
}
