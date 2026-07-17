package com.miniESB.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

/**
 * Base commune à tous les tests d'intégration.
 *
 * <p>Contrairement aux tests existants du projet (contrôleurs instanciés à la main avec des
 * services mockés), ces tests démarrent le vrai contexte Spring Boot sur un port aléatoire,
 * avec une vraie base PostgreSQL (via Testcontainers) sur laquelle les migrations Flyway
 * réelles s'exécutent. On teste donc le comportement HTTP + sécurité + persistance de bout
 * en bout, exactement comme un client réel le ferait.</p>
 *
 * <p>Le conteneur Postgres est démarré une seule fois (bloc {@code static}) et partagé par
 * toutes les classes de test du module pour limiter le temps total d'exécution.</p>
 *
 * <p>Prérequis : Docker doit être disponible en local (ou une variable d'environnement
 * DOCKER_HOST valide) pour que Testcontainers puisse démarrer le conteneur.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mini_esb_test")
                    .withUsername("mini_esb_test")
                    .withPassword("mini_esb_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway doit gérer le schéma sur cette base fraîche à chaque run.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Compte seedé par la migration Flyway V2 (V2__seed_data_admin_provider.sql). */
    protected static final String SEED_ADMIN_USERNAME = "admin";
    protected static final String SEED_ADMIN_PASSWORD = "admin1234!";

    /** Génère un identifiant unique pour éviter les collisions entre tests (unicité username/email en base). */
    protected String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** POST /auth/login puis extrait le token JWT retourné. */
    protected String loginAndGetToken(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/login", body, Map.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException("Login failed for '" + username + "': " + response);
        }
        return (String) response.getBody().get("token");
    }

    /** POST /auth/register puis extrait le token JWT retourné. Échoue le test si le compte existe déjà. */
    protected String registerAndGetToken(String username, String password, String role) {
        Map<String, String> body = Map.of("username", username, "password", password, "role", role);
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", body, Map.class);
        if (response.getStatusCode() != HttpStatus.CREATED || response.getBody() == null) {
            throw new IllegalStateException("Register failed for '" + username + "': " + response);
        }
        return (String) response.getBody().get("accessToken");
    }

    /** Raccourci pour se connecter avec le compte admin seedé par Flyway. */
    protected String adminToken() {
        return loginAndGetToken(SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD);
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
