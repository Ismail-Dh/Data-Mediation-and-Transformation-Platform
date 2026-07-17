package com.miniESB.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gestion des utilisateurs : création publique, puis administration (liste, changement
 * de rôle, mise à jour, suppression) réservée à l'ADMIN.
 */
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createUser_isPublic_andNeverExposesThePassword() {
        String username = uniqueUsername("newuser");
        Map<String, Object> body = Map.of(
                "username", username,
                "password", "Password123!",
                "role", "DEVELOPER",
                "email", username + "@example.com");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/users/add", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("username")).isEqualTo(username);
        assertThat(response.getBody().get("role")).isEqualTo("DEVELOPER");
        assertThat(response.getBody()).doesNotContainKey("password");
        assertThat(response.getBody()).doesNotContainKey("passwordHash");

        // the created account can actually log in
        String token = loginAndGetToken(username, "Password123!");
        assertThat(token).isNotBlank();
    }

    @Test
    void createUser_duplicateUsername_returnsConflict() {
        String username = uniqueUsername("dup");
        Map<String, Object> body = Map.of(
                "username", username, "password", "Password123!", "role", "DEVELOPER",
                "email", username + "@example.com");
        restTemplate.postForEntity("/api/users/add", body, Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/users/add", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminCanListUpdateAndDeleteUsers_developerCannot() {
        String admin = adminToken();
        String username = uniqueUsername("managed");
        Map<String, Object> createBody = Map.of(
                "username", username, "password", "Password123!", "role", "DEVELOPER",
                "email", username + "@example.com");
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/api/users/add", createBody, Map.class);
        Long userId = ((Number) createResponse.getBody().get("id")).longValue();

        // ADMIN lists all users, the new one and the seeded admin are both present
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .extracting(u -> ((Map<?, ?>) u).get("username"))
                .contains(username, SEED_ADMIN_USERNAME);

        // a DEVELOPER cannot list all users
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        ResponseEntity<Map> forbiddenList = restTemplate.exchange(
                "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(forbiddenList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN promotes the user to ADMIN via the dedicated role endpoint
        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                "/api/users/" + userId + "/role?role=ADMIN", HttpMethod.PUT,
                new HttpEntity<>(authHeaders(admin)), Map.class);
        assertThat(roleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roleResponse.getBody().get("role")).isEqualTo("ADMIN");

        // ADMIN updates the user's email
        Map<String, Object> updateBody = Map.of("email", "updated-" + username + "@example.com");
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/users/" + userId, HttpMethod.PATCH, new HttpEntity<>(updateBody, authHeaders(admin)), Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("email")).isEqualTo("updated-" + username + "@example.com");

        // a DEVELOPER cannot delete users
        ResponseEntity<Map> forbiddenDelete = restTemplate.exchange(
                "/api/users/" + userId, HttpMethod.DELETE, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(forbiddenDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN deletes the user
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/users/" + userId, HttpMethod.DELETE, new HttpEntity<>(authHeaders(admin)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> afterDelete = restTemplate.exchange(
                "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), List.class);
        assertThat(afterDelete.getBody())
                .extracting(u -> ((Map<?, ?>) u).get("username"))
                .doesNotContain(username);
    }
}
