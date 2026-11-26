package com.meteo.airquality;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/air-quality/hello")
    public Map<String, String> hello() {
        return Map.of("service", "AIR-QUALITY-SERVICE", "message", "Hello from Air Quality service");
    }
}
