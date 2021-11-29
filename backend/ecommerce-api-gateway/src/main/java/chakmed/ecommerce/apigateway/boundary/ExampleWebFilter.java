package chakmed.ecommerce.apigateway.boundary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Component
public class ExampleWebFilter implements WebFilter {
 
    final Logger logger =
      LoggerFactory.getLogger(ExampleWebFilter.class);
 

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        logger.info("Global Pre Filter executed");
        return chain.filter(exchange);
    }
}