# Atelier Eureka Server – Architecture Web Distribuée (2026-2027)

ESPRIT – 4IA · Projet intégré **Gestion Météo** (microservices)

## Membres de l'équipe
- Akrem Zaied
- Farah Chreif
- Hassen Smail
- Iles Zid
- Yassine Frey

## Objectif
Mettre en place un serveur de découverte **Eureka** (Netflix) et y enregistrer notre microservice
**Qualité de l'air** (`air-quality-service`) du projet Gestion Météo.
Le service est lancé en **deux instances** (ports différents) pour simuler une montée en charge :
Eureka garde la liste de toutes les instances disponibles (adresse + port), et les autres
microservices peuvent les retrouver par leur nom au lieu d'une adresse écrite en dur.

## Architecture

| Microservice        | Technologie                            | Port(s)      | Nom dans Eureka     |
|---------------------|----------------------------------------|--------------|---------------------|
| Eureka Server       | Spring Boot 3.5.6 + Spring Cloud 2025.0 | 8761         | –                   |
| Air Quality Service | Spring Boot 4.1.2 + Spring Cloud 2025.1 | 8090, 8091   | AIR-QUALITY-SERVICE |

```
Backend/eureka-server/          -> serveur de découverte (port 8761)
Backend/air-quality-service/    -> client Eureka (ports 8090 et 8091)
```

## Configuration

### Eureka Server
- Dépendance `spring-cloud-starter-netflix-eureka-server`
- Annotation `@EnableEurekaServer` sur `EurekaServerApplication`
- `application.properties` :
  ```properties
  spring.application.name=eureka-server
  server.port=8761
  eureka.client.register-with-eureka=false
  eureka.client.fetch-registry=false
  ```
  Le serveur ne s'enregistre pas lui-même et ne récupère pas le registre.

### Air Quality Service (client Eureka)
- Dépendance `spring-cloud-starter-netflix-eureka-client` + BOM `spring-cloud-dependencies` (2025.1.3)
- `application.yaml` :
  ```yaml
  spring:
    application:
      name: air-quality-service
  server:
    port: 8090
  eureka:
    client:
      service-url:
        defaultZone: http://localhost:8761/eureka
      register-with-eureka: true
      fetch-registry: true
    instance:
      prefer-ip-address: true
  ```
- Endpoint de test : `GET /api/air-quality/hello`
- Chaque instance envoie un heartbeat toutes les 30 s ; sans heartbeat pendant 90 s,
  elle est retirée du registre.

## Lancement (Windows PowerShell)

Lancer **Eureka en premier**, chaque service dans sa propre fenêtre.

**1. Eureka Server**
```powershell
cd Backend/eureka-server
.\mvnw.cmd spring-boot:run
```
Dashboard : http://localhost:8761

**2. Air Quality Service – instance 1 (port 8090)**
```powershell
cd Backend/air-quality-service
.\mvnw.cmd spring-boot:run
```

**3. Air Quality Service – instance 2 (port 8091)**
```powershell
cd Backend/air-quality-service
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8091"
```
Dans IntelliJ : Run → Edit Configurations → copier la configuration
`AirQualityServiceApplication` → VM options : `-Dserver.port=8091`.

## Vérification
- http://localhost:8090/api/air-quality/hello
- http://localhost:8091/api/air-quality/hello
- Le dashboard Eureka affiche **AIR-QUALITY-SERVICE UP (2)** avec les instances
  `localhost:air-quality-service:8090` et `localhost:air-quality-service:8091`.

## Capture du dashboard Eureka
![Dashboard Eureka](capture-eureka.png)

> Placer la capture dans `Backend/eureka-server/capture-eureka.png`.
