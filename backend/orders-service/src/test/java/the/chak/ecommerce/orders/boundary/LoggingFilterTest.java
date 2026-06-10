package the.chak.ecommerce.orders.boundary;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;

class LoggingFilterTest {

    private final LoggingFilter filter = new LoggingFilter();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.remove("requestId");
    }

    @Test
    @DisplayName("Does not load a requestId into MDC now that X-Request-ID is retired")
    void filter_doesNotPopulateRequestIdInMdc() {
        UriInfo info = mock(UriInfo.class);
        when(info.getPath()).thenReturn("orders");
        filter.info = info;

        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getMethod()).thenReturn("GET");

        filter.filter(ctx);

        assertNull(MDC.get("requestId"),
                "requestId must not be loaded into MDC; correlation is carried by traceId/spanId");
    }
}
