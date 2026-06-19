package the.chak.ecommerce.apigateway;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

/**
 * Connects the Logback {@link OpenTelemetryAppender} (declared in {@code logback-spring.xml}) to
 * the application's OpenTelemetry SDK so log records ship over OTLP to the Collector.
 *
 * <p>Spring Boot 3.4 auto-configures the OTLP {@code LoggerProvider} and exporter from
 * {@code management.otlp.logging.*}, but it never calls {@link OpenTelemetryAppender#install}.
 * Without this the appender stays detached from the SDK and silently drops every record (only
 * stdout keeps working). Installing on {@link ApplicationReadyEvent} flushes the records the
 * appender buffered during startup once the {@link OpenTelemetry} bean is fully initialised.
 */
@Configuration
public class OpenTelemetryLogbackConfig implements ApplicationListener<ApplicationReadyEvent> {

	private final OpenTelemetry openTelemetry;

	public OpenTelemetryLogbackConfig(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		OpenTelemetryAppender.install(openTelemetry);
	}
}
