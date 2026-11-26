# TP6 – Implémentation API Gateway (Gestion Météo – AWD 2026-2027)

## 1. Architecture

```
                         Client (navigateur / Postman)
                                     |
                                     v
                    +----------------------------------+
                    |   API Gateway  :8090             |   Spring Cloud Gateway (WebFlux)
                    |   - routes statiques / lb://      |   + Eureka Client + LoadBalancer
                    |   - Discovery Locator (dynamique) |   + springdoc (Swagger UI unifié)
                    |   - Circuit Breaker (meetingCB)   |   + Resilience4j
                    +----------------------------------+
                       |  découverte         |  routage (Round Robin / Random)
                       v                     v
          +-----------------------+    +------------------------------------------+
          | Eureka Server  :8761  |<---| MEETING       :8083, :8085 (Node/Express) |
          +-----------------------+    | NOTIFICATION  :8084, :8086 (Python/FastAPI)|
                                       +------------------------------------------+
```

| Composant | Techno | Port(s) | Nom Eureka |
|---|---|---|---|
| eureka-server | Spring Boot 3.5.7 / Cloud 2025.0.0 | 8761 | – |
| api-gateway | Spring Boot 4.1.2 / Cloud 2025.1.4 (Gateway 5.0.4), Java 17 | **8090** | API-GATEWAY |
| meeting-service | Node.js 24 / Express 5 / eureka-js-client | 8083 (+ 8085) | MEETING |
| notification-service | Python 3.12 / FastAPI / py_eureka_client | 8084 (+ 8086) | NOTIFICATION |

Le port 8080 est occupé par Oracle TNS Listener et WSL (`wslrelay`). Ces processus n'ont pas été touchés : le Gateway utilise **8090**.

## 2. Routage

### 2.1 Routage statique : Partie 2, méthode 1 (classe Java, URI directe, sans Eureka)
`tn.esprit.gateway.config.StaticRoutesConfig`

| URL Gateway | Destination |
|---|---|
| `GET /static/api/meetings/hello` | `http://localhost:8083/api/meetings/hello` |
| `GET /static/api/notifications/hello` | `http://localhost:8084/api/notifications/hello` |

Le préfixe `/static` est retiré par `StripPrefix=1`. Ces routes fonctionnent même si Eureka est arrêté, mais elles cassent si le port du service change.

### 2.2 Routes déclarées avec `lb://` : Partie 3, méthode 2 (`application.yml`)

| Route | URL Gateway | Destination |
|---|---|---|
| meeting-route | `GET /api/meetings/**` | `lb://MEETING` (+ Circuit Breaker) |
| notification-route | `GET /api/notifications/**` | `lb://NOTIFICATION` |
| meeting-api-docs | `GET /api-docs/meeting` | `lb://MEETING` → `/v3/api-docs` |
| notification-api-docs | `GET /api-docs/notification` | `lb://NOTIFICATION` → `/openapi.json` |

### 2.3 Routage dynamique (Discovery Locator)
`spring.cloud.gateway.server.webflux.discovery.locator.enabled=true` crée une route **pour chaque service présent dans Eureka**, sans rien déclarer : `/<service-en-minuscules>/** → lb://<SERVICE>` (le préfixe est retiré).

| URL Gateway | Route générée |
|---|---|
| `GET /meeting/api/meetings/hello` | `ReactiveCompositeDiscoveryClient_MEETING` |
| `GET /notification/api/notifications/hello` | `ReactiveCompositeDiscoveryClient_NOTIFICATION` |

### 2.4 Statique ou dynamique ?

| | Statique (`http://localhost:PORT`) | Dynamique (`lb://SERVICE` + Eureka) |
|---|---|---|
| Dépend d'Eureka | Non | Oui |
| Connaît l'adresse du service | Oui, codée en dur | Non : résolue à l'exécution |
| Plusieurs instances / load balancing | Non (1 seule cible) | Oui |
| Changement de port / IP | La route casse | Transparent |
| Nouvelle route pour un nouveau service | À écrire | Automatique (Discovery Locator) |

## 3. Load balancing (Partie 3, questions 2.b et 3)

Les logs demandés par l'énoncé sont activés (`logging.level.org.springframework.cloud.gateway=DEBUG`, `reactor.netty.http.client=DEBUG`). Un filtre d'observation, `LoadBalancerLoggingFilter`, ajoute en plus une ligne de synthèse par requête : stratégie utilisée et instance choisie.

### 3.1 Stratégie par défaut : **Round Robin** (question 2.b)
NOTIFICATION, sans configuration particulière, avec 2 instances (8084 et 8086). Extrait de `preuves/logs-load-balancing.txt` :
```
[LB] route=notification-route service=NOTIFICATION strategy=RoundRobinLoadBalancer -> instance=localhost:notification:8086
[LB] route=notification-route service=NOTIFICATION strategy=RoundRobinLoadBalancer -> instance=localhost:notification:8084
[LB] route=notification-route service=NOTIFICATION strategy=RoundRobinLoadBalancer -> instance=localhost:notification:8086
[LB] route=notification-route service=NOTIFICATION strategy=RoundRobinLoadBalancer -> instance=localhost:notification:8084
```
Les instances alternent strictement (8084, 8086, 8084, 8086…) : c'est le `RoundRobinLoadBalancer`, la stratégie par défaut de Spring Cloud LoadBalancer.

### 3.2 Autres stratégies existantes
* **Round Robin** (`RoundRobinLoadBalancer`) : par défaut, rotation circulaire.
* **Random** (`RandomLoadBalancer`) : instance tirée au hasard.
* **Weighted** (`WeightedServiceInstanceListSupplier`) : selon un poids par instance.
* **Same instance preference** : réutilise l'instance précédente.
* **Request-based sticky session** : basée sur un cookie.
* **Zone preference** : instances de la même zone.
* **Health-check** : ne garde que les instances saines.
* Historiques (Netflix Ribbon, déprécié) : *Weighted Response Time*, *Zone Avoidance*, *Best Available*.

### 3.3 Random appliqué uniquement à MEETING (question 3)
Le projet n'a pas de service « Candidat » : la stratégie est appliquée au service réel **MEETING**.

* `config/LoadBalancerConfiguration.java` : `@LoadBalancerClients(@LoadBalancerClient(name = "MEETING", configuration = RandomLoadBalancerConfig.class), …)`
* `config/RandomLoadBalancerConfig.java` : bean `ReactorLoadBalancer<ServiceInstance>` → `new RandomLoadBalancer(...)` (réponse au `TODO` de l'énoncé). Cette classe n'est **pas** annotée `@Configuration`, sinon elle deviendrait la stratégie de tous les services.

Résultat (2 instances : 8083 et 8085) :
```
[LB] route=meeting-route service=MEETING strategy=RandomLoadBalancer -> instance=localhost:meeting:8083
[LB] route=meeting-route service=MEETING strategy=RandomLoadBalancer -> instance=localhost:meeting:8083
[LB] route=meeting-route service=MEETING strategy=RandomLoadBalancer -> instance=localhost:meeting:8083
[LB] route=meeting-route service=MEETING strategy=RandomLoadBalancer -> instance=localhost:meeting:8085
[LB] route=meeting-route service=MEETING strategy=RandomLoadBalancer -> instance=localhost:meeting:8085
```
La séquence est irrégulière (plusieurs fois de suite la même instance), ce qui est le comportement attendu d'un tirage aléatoire. Au même moment, NOTIFICATION reste en Round Robin : la stratégie Random ne s'applique bien qu'à MEETING.

## 4. Documentation OpenAPI / Swagger (Partie 4)

Portail unifié : **http://localhost:8090/swagger-ui.html** (liste « Select a definition »).

| Source | Contrat | Origine |
|---|---|---|
| meeting-service (MEETING) | `/api-docs/meeting` → `/v3/api-docs` | Document OpenAPI 3.0.3 ajouté dans `server.js` : Express n'en génère pas. Il décrit l'endpoint réel `/api/meetings/hello`. |
| notification-service (NOTIFICATION) | `/api-docs/notification` → `/openapi.json` | Généré nativement par FastAPI (aucune modification) |
| api-gateway (fallbacks) | `/v3/api-docs` | springdoc (contrôleur de repli) |

Gateway : `springdoc-openapi-starter-webflux-ui` **3.2.0**, construit sur Spring Boot 4.1.2 (même version que le projet ; `measurement-service` utilise déjà springdoc 3.2.0). Variante *webflux*, et non *webmvc* comme dans l'énoncé, car Spring Cloud Gateway est réactif.

Captures : `captures/swagger-meeting.png`, `captures/swagger-notification.png`.

## 5. Postman

Collection : `postman/TP6-API-Gateway.postman_collection.json` (variable `gateway = http://localhost:8090`). 12 requêtes, toutes via le Gateway, chacune avec des tests (`pm.test`) :

| Dossier | Requête | Réponse attendue |
|---|---|---|
| 1. lb:// | MEETING `GET /api/meetings/hello` | 200 `{"service":"MEETING","port":8083\|8085,...}` |
| 1. lb:// | NOTIFICATION `GET /api/notifications/hello` | 200 `{"service":"NOTIFICATION","port":8084\|8086,...}` |
| 2. statique | `GET /static/api/meetings/hello` | 200, port 8083 |
| 2. statique | `GET /static/api/notifications/hello` | 200, port 8084 |
| 3. dynamique | `GET /meeting/api/meetings/hello` | 200 MEETING |
| 3. dynamique | `GET /notification/api/notifications/hello` | 200 NOTIFICATION |
| 4. OpenAPI | `/v3/api-docs/swagger-config`, `/api-docs/meeting`, `/api-docs/notification` | 200 |
| 5. Supervision | `/actuator/health`, `/actuator/gateway/routes`, `/actuator/circuitbreakers` | 200 |

Headers : `Accept: application/json`. Aucun body (requêtes GET).
Exécution réelle : **26 assertions OK, 0 en échec** (voir `preuves/resultat-execution-collection.txt`). Pour l'importer : Postman → *Import* → choisir le fichier JSON → *Run collection*.

## 6. Bonus : Circuit Breaker (facultatif dans l'énoncé)

* Dépendance `spring-cloud-starter-circuitbreaker-reactor-resilience4j`.
* Filtre `CircuitBreaker` sur `meeting-route` : `name=meetingCB`, `fallbackUri=forward:/fallback/meeting`.
* `web/FallbackController` renvoie `{"service":"MEETING","message":"Service Meeting temporairement indisponible","degraded":true,...}`.
* Paramètres : fenêtre de 4 appels, minimum 2 appels, seuil de 50 %, 15 s en état OPEN, timeout de 5 s.

Test réel : les 2 instances MEETING ont été arrêtées, puis 5 appels envoyés.
```
appel 1..5 -> HTTP 200 {"message":"Service Meeting temporairement indisponible","degraded":true,...}
/actuator/circuitbreakers -> meetingCB: failedCalls=2, failureRate=50.0%, notPermittedCalls=3, state=OPEN
```
Après le redémarrage de MEETING et 15 s d'attente : réponses normales (8083/8085) et `state=CLOSED`.

## 7. Démarrage (PowerShell, une fenêtre par service)

```powershell
# 1. Eureka (ou Run depuis IntelliJ)
cd C:\Users\ilyes\Documents\Meteo-4SSA15-G4-eureka\Backend\eureka-server
.\mvnw.cmd spring-boot:run

# 2. MEETING - instance 1 (8083) puis instance 2 (8085)
cd C:\Users\ilyes\Documents\Meteo-4SSA15-G4-eureka\Backend\meeting-service
node server.js
$env:PORT = "8085"; node server.js        # dans une 2e fenêtre

# 3. NOTIFICATION - instance 1 (8084) puis instance 2 (8086)
cd C:\Users\ilyes\Documents\Meteo-4SSA15-G4-eureka\Backend\notification-service
py -3 main.py                              # "python" n'est pas dans le PATH : utiliser "py"
$env:PORT = "8086"; py -3 main.py         # dans une 2e fenêtre

# 4. API Gateway (8090)
cd C:\Users\ilyes\Documents\Meteo-4SSA15-G4-eureka\Backend\api-gateway
mvn clean package -DskipTests
java -jar target\api-gateway-0.0.1-SNAPSHOT.jar
```

Vérifications rapides :
```powershell
Invoke-RestMethod http://localhost:8090/api/meetings/hello
Invoke-RestMethod http://localhost:8090/api/notifications/hello
1..10 | ForEach-Object { (Invoke-RestMethod http://localhost:8090/api/notifications/hello).port }   # 8084 8086 8084 ...
1..10 | ForEach-Object { (Invoke-RestMethod http://localhost:8090/api/meetings/hello).port }        # aléatoire
Start-Process http://localhost:8761                  # tableau de bord Eureka
Start-Process http://localhost:8090/swagger-ui.html  # Swagger unifié
```

Délais à connaître : le Gateway relit le registre Eureka toutes les 5 s et garde la liste d'instances en cache 5 s. Une nouvelle instance est donc routée après environ 10 à 30 s (le temps qu'elle s'enregistre).

## 8. Limitations connues

1. **Les services Spring Boot de l'équipe ne contiennent aucun contrôleur REST**, sur aucune branche git : `air-quality-service`, `alert_service`, `Forcast`, `measurement-service`, `station-service`, `user-service`. Ils n'ont donc aucun endpoint à router, documenter ou tester. Aucun faux endpoint n'a été créé. De plus :
   * `air-quality-service` et `user-service` n'ont pas de starter web ;
   * `Forcast` et `station-service` n'ont pas de client Eureka et démarreraient sur 8080 (conflit) ;
   * `alert_service` et `measurement-service` dépendent de `spring-cloud-starter-config` sans `spring.config.import`, ainsi que de PostgreSQL / MongoDB.

   **Correction minimale** quand un contrôleur existera : ajouter `spring-cloud-starter-netflix-eureka-client` (`alert` et `measurement` l'ont déjà), un `server.port` libre, `spring.cloud.config.enabled=false` (ou un Config Server), et `springdoc-openapi-starter-webmvc-ui` 3.2.0. Le service sera alors routé **automatiquement** par le Discovery Locator (`/<nom-service>/**`). Pour Swagger, ajouter une route `/api-docs/<service>` → `SetPath=/v3/api-docs`, et une entrée dans `springdoc.swagger-ui.urls`.
2. Eureka garde des instances `RISK-SERVICE` / `RISK-ZONE-SERVICE` venant d'un autre worktree (`feature/risk-management`) : le Discovery Locator crée des routes pour elles, mais ces services ne font pas partie de ce TP.
3. Sous Windows, arrêter un service Node/Python en tuant son processus ne le désenregistre pas d'Eureka : son instance reste listée jusqu'à expiration du bail (le self-preservation d'Eureka peut la garder plus longtemps). Utilisez plutôt Ctrl+C.
