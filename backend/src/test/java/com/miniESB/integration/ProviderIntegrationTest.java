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
 * Cycle de vie complet d'un Provider via la vraie stack HTTP + base Postgres réelle.
 */
class ProviderIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullCrudLifecycle_asAdmin() {
        String admin = adminToken();

        // CREATE
        Map<String, Object> createBody = Map.of(
                "name", "Payment Gateway " + uniqueUsername("p"),
                "endpoint", "http://payment.example.com",
                "protocol", "HTTP",
                "timeout", 5000);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/providers", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(admin)), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number id = (Number) createResponse.getBody().get("id");
        assertThat(id).isNotNull();
        assertThat(createResponse.getBody().get("endpoint")).isEqualTo("http://payment.example.com");

        // READ
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/providers/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) getResponse.getBody().get("timeout")).intValue()).isEqualTo(5000);

        // UPDATE (partial)
        Map<String, Object> updateBody = Map.of("timeout", 9999);
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/providers/" + id, HttpMethod.PATCH, new HttpEntity<>(updateBody, authHeaders(admin)), Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updateResponse.getBody().get("timeout")).intValue()).isEqualTo(9999);
        // le nom n'a pas été touché par le PATCH partiel
        assertThat(updateResponse.getBody().get("name")).isEqualTo(createBody.get("name"));

        // update actually persisted (re-fetch)
        ResponseEntity<Map> refetch = restTemplate.exchange(
                "/api/providers/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), Map.class);
        assertThat(((Number) refetch.getBody().get("timeout")).intValue()).isEqualTo(9999);

        // LIST contains it (+ le "Default Provider" seedé par Flyway V2)
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/providers", HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody().size()).isGreaterThanOrEqualTo(2);

        // DELETE
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/providers/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders(admin)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // GET after delete -> 404
        ResponseEntity<Map> afterDelete = restTemplate.exchange(
                "/api/providers/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), Map.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void developer_canReadButNotCreateProviders() {
        String dev = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/providers", HttpMethod.GET, new HttpEntity<>(authHeaders(dev)), List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = Map.of(
                "name", "X", "endpoint", "http://x.example.com", "protocol", "HTTP", "timeout", 10);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/providers", HttpMethod.POST, new HttpEntity<>(body, authHeaders(dev)), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createProvider_withBlankName_returnsBadRequest() {
        String admin = adminToken();
        Map<String, Object> body = Map.of(
                "name", "", "endpoint", "http://x.example.com", "protocol", "HTTP", "timeout", 10);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/providers", HttpMethod.POST, new HttpEntity<>(body, authHeaders(admin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUnknownProvider_returnsNotFound() {
        String admin = adminToken();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/providers/999999", HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
