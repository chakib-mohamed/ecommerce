package chakmed.ecommerce.apigateway;

import chakmed.ecommerce.apigateway.boundary.ExampleWebFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
@EnableWebFluxSecurity// Enable security config. This annotation denotes config for spring security.
public class SecurityConfig {
	private final JwtConfig jwtConfig;

	private final ReactiveAuthenticationManager authenticationManager;

	private final ServerSecurityContextRepository securityContextRepository;

	@Autowired
	private
	Customizer<ServerHttpSecurity.CorsSpec> corsCustomizer;

	@Autowired
	ExampleWebFilter webFilter;

	public SecurityConfig(JwtConfig jwtConfig, ReactiveAuthenticationManager authenticationManager, ServerSecurityContextRepository securityContextRepository) {
		this.jwtConfig = jwtConfig;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.corsCustomizer = corsCustomizer;
	}

	@Bean
	SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
		Optional.ofNullable(this.corsCustomizer).map(http::cors);
		return http
				.csrf().disable()
				.exceptionHandling()
				.authenticationEntryPoint((swe, e) -> Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)))
				.accessDeniedHandler((swe, e) -> Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN)))
				.and()
				.addFilterBefore(webFilter, SecurityWebFiltersOrder.CORS)
				.securityContextRepository(securityContextRepository)
				.authorizeExchange()
				.pathMatchers(jwtConfig.getUri()).permitAll()
				.pathMatchers("/actuator/**").permitAll()
				.pathMatchers(HttpMethod.POST, "/api/users").permitAll()
				.pathMatchers(HttpMethod.OPTIONS).permitAll()
				.pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
				.anyExchange().authenticated()
				.and()
				.build();
	}

}