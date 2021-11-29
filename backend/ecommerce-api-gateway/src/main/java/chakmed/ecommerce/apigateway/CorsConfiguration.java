package chakmed.ecommerce.apigateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;


@Configuration
@Profile("dev")
public class CorsConfiguration {

  @Value("${cors.allowedOrigins}")
  String corsAllowedOrigins;

  private static final String ALLOWED_HEADERS = "x-requested-with, authorization, Content-Type, Authorization, credential, X-XSRF-TOKEN";
  private static final String ALLOWED_METHODS = "GET, PUT, POST, DELETE, OPTIONS";
  private static final String MAX_AGE = "3600";

  @Bean
  public Customizer<ServerHttpSecurity.CorsSpec> corsCustomizer() {
    return (corsSpec) ->
       corsConfigurationSource()
    ;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    return (ServerWebExchange ctx) -> {
      org.springframework.web.cors.CorsConfiguration corsConfiguration = new org.springframework.web.cors.CorsConfiguration();
      corsConfiguration.setAllowedOrigins(Arrays.asList(corsAllowedOrigins));
      for (String allowedHeader:
              ALLOWED_HEADERS.split(", ")) {
        corsConfiguration.addAllowedHeader(allowedHeader);
      }
      corsConfiguration.setAllowCredentials(true);
      for (String allowedMethod:
           ALLOWED_METHODS.split(", ")) {
        corsConfiguration.addAllowedMethod(allowedMethod);
      }
      corsConfiguration.setMaxAge(Long.valueOf(MAX_AGE));
      // corsConfiguration.applyPermitDefaultValues();
      return corsConfiguration;
    };
  }

}