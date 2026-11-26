package tn.esprit.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtre d'observation (aucune modification de la requete) execute juste apres
 * le filtre de load balancing de la Gateway. Il trace, pour chaque requete
 * routee via lb://, la strategie utilisee et l'instance Eureka choisie :
 *
 *   [LB] route=notification-route service=NOTIFICATION strategy=RoundRobinLoadBalancer
 *        -> instance=localhost:notification:8086 (127.0.0.1:8086)
 *
 * Il sert de preuve dans les logs pour les questions 2.b et 3 de la Partie 3.
 */
@Component
public class LoadBalancerLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerLoggingFilter.class);

    private final LoadBalancerClientFactory loadBalancerClientFactory;

    public LoadBalancerLoggingFilter(LoadBalancerClientFactory loadBalancerClientFactory) {
        this.loadBalancerClientFactory = loadBalancerClientFactory;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Response<ServiceInstance> lbResponse =
                exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (lbResponse != null && lbResponse.hasServer()) {
            ServiceInstance instance = lbResponse.getServer();
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            Object strategy = loadBalancerClientFactory.getInstance(instance.getServiceId());
            log.info("[LB] route={} service={} strategy={} -> instance={} ({}:{})",
                    route != null ? route.getId() : "?",
                    instance.getServiceId(),
                    strategy != null ? strategy.getClass().getSimpleName() : "?",
                    instance.getInstanceId(),
                    instance.getHost(),
                    instance.getPort());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER + 1;
    }
}
