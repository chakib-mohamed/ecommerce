package the.chak.ecommerce.apigateway;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
	private final JwtConfig jwtConfig;

	private final ServerSecurityContextRepository securityContextRepository;

	public SecurityConfig(JwtConfig jwtConfig,
			ServerSecurityContextRepository securityContextRepository) {
		this.jwtConfig = jwtConfig;
		this.securityContextRepository = securityContextRepository;
	}

	@Bean
	@Profile("dev")
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
		configuration.setAllowedMethods(
				Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	@Profile("!dev")
	public CorsConfigurationSource disabledCorsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		return source;
	}

	@Bean
	SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
		return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
				.exceptionHandling(
						exceptionHandling -> exceptionHandling
								.authenticationEntryPoint((swe,
										e) -> Mono.fromRunnable(() -> swe.getResponse()
												.setStatusCode(HttpStatus.UNAUTHORIZED)))
								.accessDeniedHandler((swe,
										e) -> Mono.fromRunnable(() -> swe.getResponse()
												.setStatusCode(HttpStatus.FORBIDDEN))))
				// .addFilterBefore(webFilter, SecurityWebFiltersOrder.CORS)
				.securityContextRepository(securityContextRepository)
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(jwtConfig.getUri()).permitAll()
						.pathMatchers("/actuator/**").permitAll()
						.pathMatchers(HttpMethod.POST, "/api/users").permitAll()
						.pathMatchers(HttpMethod.OPTIONS).permitAll()
						.pathMatchers(HttpMethod.GET, "/api/featured-products/**").permitAll()
						.pathMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/promotions/**").permitAll()
						.anyExchange().authenticated())
				.build();
	}

}
