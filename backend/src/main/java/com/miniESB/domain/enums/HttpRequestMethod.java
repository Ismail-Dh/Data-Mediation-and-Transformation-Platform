package com.miniESB.domain.enums;

/**
 * Méthode HTTP utilisée pour transmettre le payload mappé vers un provider.
 *
 * Chaque provider peut désormais définir sa propre méthode (au lieu du POST
 * codé en dur historiquement) — certains providers exposent des endpoints
 * REST attendant un GET, un PUT (upsert) ou un PATCH (update partiel).
 */
public enum HttpRequestMethod {
    GET,
    POST,
    PUT,
    PATCH
}