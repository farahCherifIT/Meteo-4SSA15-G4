package tn.esprit.gateway.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Configuration du LoadBalancer Random (reponse au squelette de l'enonce).
 *
 * IMPORTANT : cette classe n'est volontairement PAS annotee @Configuration.
 * Si elle l'etait, le scan de composants l'enregistrerait dans le contexte
 * principal et le RandomLoadBalancer deviendrait la strategie de TOUS les
 * services. Elle n'est chargee que dans le contexte du client nomme via
 * {@link LoadBalancerConfiguration}.
 */
public class RandomLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(Environment environment,
                                                                   LoadBalancerClientFactory factory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RandomLoadBalancer(
                factory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
