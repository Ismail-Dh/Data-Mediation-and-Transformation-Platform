package com.miniESB.controller;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;

import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;

import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.PayloadService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("PayloadController — unit tests")
class PayloadControllerTest {

    @Mock  private PayloadService payloadService;
    @InjectMocks private PayloadController controller;

    private final PayloadResponse validatedPayload = new PayloadResponse(
            10L, "{\"orderId\":\"123\"}", DataFormat.JSON,
            PayloadStatus.VALIDATED, LocalDateTime.now(), 1L);

    private final PayloadResponse failedPayload = new PayloadResponse(
            11L, "{}", DataFormat.JSON,
            PayloadStatus.FAILED, LocalDateTime.now(), 1L);

    @Nested
    @DisplayName("receivePayload()")
    class ReceivePayload {

        @Test
        @DisplayName("returns 201 with VALIDATED payload on successful submission")
        void receivePayload_returns201() {
            PayloadRequest req = new PayloadRequest("{\"orderId\":\"123\"}", "JSON");
            when(payloadService.receivePayload(1L, req)).thenReturn(validatedPayload);

            ResponseEntity<PayloadResponse> response = controller.receivePayload(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().status()).isEqualTo(PayloadStatus.VALIDATED);
            assertThat(response.getBody().pipelineId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("propagates PayloadValidationException on structural violations")
        void receivePayload_validationFails() {
            PayloadRequest req = new PayloadRequest("{}", "JSON");
            List<FieldViolation> violations = List.of(
                    new FieldViolation("orderId", "MISSING_FIELD", "Field is required"),
                    new FieldViolation("email",   "MISSING_FIELD", "Field is required"));

            when(payloadService.receivePayload(1L, req))
                    .thenThrow(new PayloadValidationException(violations));

            assertThatThrownBy(() -> controller.receivePayload(1L, req))
                    .isInstanceOf(PayloadValidationException.class)
                    .satisfies(ex -> {
                        PayloadValidationException pve = (PayloadValidationException) ex;
                        assertThat(pve.getViolations()).hasSize(2);
                        assertThat(pve.getViolations().get(0).errorType()).isEqualTo("MISSING_FIELD");
                    });
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void receivePayload_pipelineNotFound() {
            PayloadRequest req = new PayloadRequest("{}", "JSON");
            when(payloadService.receivePayload(99L, req))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> controller.receivePayload(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPayloads()")
    class GetPayloads {

        @Test
        @DisplayName("returns 200 with list of payloads for pipeline")
        void getPayloads_returns200() {
            when(payloadService.getPayloadsByPipeline(1L))
                    .thenReturn(List.of(validatedPayload, failedPayload));

            ResponseEntity<List<PayloadResponse>> response = controller.getPayloads(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when pipeline has no payloads")
        void getPayloads_empty() {
            when(payloadService.getPayloadsByPipeline(1L)).thenReturn(List.of());

            ResponseEntity<List<PayloadResponse>> response = controller.getPayloads(1L);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void getPayloads_pipelineNotFound() {
            when(payloadService.getPayloadsByPipeline(99L))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> controller.getPayloads(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPayload()")
    class GetPayload {

        @Test
        @DisplayName("returns 200 with payload when found")
        void getPayload_returns200() {
            when(payloadService.getPayloadById(10L)).thenReturn(validatedPayload);

            ResponseEntity<PayloadResponse> response = controller.getPayload(1L, 10L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(10L);
            assertThat(response.getBody().status()).isEqualTo(PayloadStatus.VALIDATED);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when payload not found")
        void getPayload_notFound() {
            when(payloadService.getPayloadById(99L))
                    .thenThrow(new ResourceNotFoundException("Payload not found"));

            assertThatThrownBy(() -> controller.getPayload(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
