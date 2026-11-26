package tn.esprit.gateway.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * TP6 - Bonus : reponse de repli renvoyee par le Circuit Breaker "meetingCB"
 * (route meeting-route) lorsque le service MEETING est indisponible ou que
 * le circuit est OPEN.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/meeting")
    public Mono<ResponseEntity<Map<String, Object>>> meetingFallback() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "service", "MEETING",
                "message", "Service Meeting temporairement indisponible",
                "degraded", true,
                "timestamp", Instant.now().toString())));
    }
}
