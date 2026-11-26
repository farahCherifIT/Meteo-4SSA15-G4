package tn.esprit.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TP6 - Partie 2, Methode 1 : configuration STATIQUE via une classe Java.
 *
 * Ces routes pointent directement vers l'adresse reelle du microservice
 * (http://localhost:PORT) : elles n'utilisent PAS Eureka et fonctionnent meme
 * si Eureka est arrete. Si le port du service change, elles cassent.
 *
 * Le prefixe /static permet de les distinguer des routes lb:// (application.yml)
 * qui utilisent le meme chemin /api/... ; StripPrefix=1 retire "/static"
 * avant de transmettre la requete.
 *
 *   GET /static/api/meetings/hello       -> http://localhost:8083/api/meetings/hello
 *   GET /static/api/notifications/hello  -> http://localhost:8084/api/notifications/hello
 */
@Configuration
public class StaticRoutesConfig {

    @Bean
    public RouteLocator staticRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("meeting-static", r -> r.path("/static/api/meetings/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("http://localhost:8083"))
                .route("notification-static", r -> r.path("/static/api/notifications/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("http://localhost:8084"))
                .build();
    }
}
