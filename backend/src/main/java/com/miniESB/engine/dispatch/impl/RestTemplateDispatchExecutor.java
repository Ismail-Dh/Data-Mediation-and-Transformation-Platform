package com.miniESB.engine.dispatch.impl;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.engine.dispatch.DispatchOutcome;
import com.miniESB.engine.dispatch.HttpDispatchExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestTemplateDispatchExecutor implements HttpDispatchExecutor {

    private final RestTemplateBuilder restTemplateBuilder;

    @Override
    public DispatchOutcome call(String endpoint, HttpMethod method, String body,
                                 DataFormat outputFormat, int timeoutSec, String providerLabel) {
        long start = System.currentTimeMillis();
        log.info("{} {} → provider='{}'", method, endpoint, providerLabel);

        try {
            RestTemplate rt = buildRestTemplate(timeoutSec);
            HttpEntity<String> request = new HttpEntity<>(body, buildHeaders(outputFormat));
            ResponseEntity<String> response = rt.exchange(endpoint, method, request, String.class);

            long duration = System.currentTimeMillis() - start;
            int status = response.getStatusCode().value();
            boolean ok = response.getStatusCode().is2xxSuccessful();

            log.info("Provider '{}' → HTTP {} in {}ms", providerLabel, status, duration);

            return ok
                    ? DispatchOutcome.success(status, response.getBody(), duration)
                    : DispatchOutcome.httpError(status, response.getBody(), duration);

        } catch (HttpStatusCodeException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Provider '{}' HTTP {} — {}", providerLabel, e.getStatusCode().value(), e.getMessage());
            return DispatchOutcome.httpError(e.getStatusCode().value(), e.getResponseBodyAsString(), duration);

        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "Network error → " + providerLabel + ": " + e.getMessage();
            log.error(msg);
            return DispatchOutcome.networkError(duration, msg);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String msg = "Unexpected error → " + providerLabel + ": " + e.getMessage();
            log.error(msg, e);
            return DispatchOutcome.networkError(duration, msg);
        }
    }

    private RestTemplate buildRestTemplate(int timeoutSec) {
        Duration t = Duration.ofSeconds(timeoutSec > 0 ? timeoutSec : 30);
        return restTemplateBuilder.connectTimeout(t).readTimeout(t).build();
    }

    private HttpHeaders buildHeaders(DataFormat outputFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(outputFormat == DataFormat.XML ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.ALL));
        return headers;
    }
}
