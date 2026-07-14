package com.miniESB.engine.dispatch.impl;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.engine.dispatch.DispatchOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestTemplateDispatchExecutor — unit tests")
class RestTemplateDispatchExecutorTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private RestTemplateDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        executor = new RestTemplateDispatchExecutor(restTemplateBuilder);
    }

    @Test
    @DisplayName("returns a success outcome for a 2xx response")
    void successOutcome() {
        ResponseEntity<String> response = new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        DispatchOutcome outcome = executor.call("http://provider/api", HttpMethod.POST, "{}",
                DataFormat.JSON, 5, "myProvider");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.rawBody()).isEqualTo("{\"ok\":true}");
        assertThat(outcome.errorMessage()).isNull();
    }

    @Test
    @DisplayName("returns an http-error outcome for a non-2xx response")
    void httpErrorOutcomeFromResponse() {
        ResponseEntity<String> response = new ResponseEntity<>("error-body", HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        DispatchOutcome outcome = executor.call("http://provider/api", HttpMethod.POST, "{}",
                DataFormat.JSON, 5, "myProvider");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(400);
        assertThat(outcome.rawBody()).isEqualTo("error-body");
    }

    @Test
    @DisplayName("classifies HttpStatusCodeException as an http-error outcome")
    void httpErrorOutcomeFromException() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, "not-found-body".getBytes(), null);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(ex);

        DispatchOutcome outcome = executor.call("http://provider/api", HttpMethod.GET, null,
                DataFormat.JSON, 5, "myProvider");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(404);
        assertThat(outcome.rawBody()).isEqualTo("not-found-body");
    }

    @Test
    @DisplayName("classifies ResourceAccessException (network failure) as a network-error outcome")
    void networkErrorOutcome() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        DispatchOutcome outcome = executor.call("http://provider/api", HttpMethod.GET, null,
                DataFormat.JSON, 5, "myProvider");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isZero();
        assertThat(outcome.rawBody()).isNull();
        assertThat(outcome.errorMessage()).contains("myProvider");
    }

    @Test
    @DisplayName("classifies any other unexpected exception as a network-error outcome")
    void unexpectedErrorOutcome() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("boom"));

        DispatchOutcome outcome = executor.call("http://provider/api", HttpMethod.GET, null,
                DataFormat.JSON, 5, "myProvider");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorMessage()).contains("Unexpected error");
    }
}
