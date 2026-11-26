package tn.esprit.gateway.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * TP6 - Partie 3, Question 3 : strategie Random appliquee UNIQUEMENT au
 * service MEETING (equivalent du service "Candidat" de l'enonce).
 *
 * Tous les autres services (NOTIFICATION, ...) gardent la strategie par
 * defaut de Spring Cloud LoadBalancer : RoundRobinLoadBalancer.
 *
 * Le nom du client LoadBalancer est l'hote de l'URI lb:// et il est sensible a
 * la casse (un contexte par nom). Les routes explicites (lb://MEETING) et la
 * route dynamique du Discovery Locator (verifie dans les logs : service=MEETING)
 * utilisent "MEETING". L'entree "meeting" couvre une route ecrite en minuscules
 * (lb://meeting) afin que MEETING reste en Random quelle que soit l'ecriture.
 */
@Configuration
@LoadBalancerClients({
        @LoadBalancerClient(name = "MEETING", configuration = RandomLoadBalancerConfig.class),
        @LoadBalancerClient(name = "meeting", configuration = RandomLoadBalancerConfig.class)
})
public class LoadBalancerConfiguration {
}
