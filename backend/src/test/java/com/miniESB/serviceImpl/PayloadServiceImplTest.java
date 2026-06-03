package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.StructuralValidatorService;
import com.miniESB.service.impl.PayloadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayloadServiceImpl — unit tests")
class PayloadServiceImplTest {

    @Mock private PayloadRepository         payloadRepository;
    @Mock private PipelineRepository         pipelineRepository;
    @Mock private PipelineFieldRepository    pipelineFieldRepository;
    @Mock private StructuralValidatorService structuralValidatorService;

    @InjectMocks
    private PayloadServiceImpl payloadService;

    private Pipeline pipeline;
    private PayloadRequest validRequest;
    private Payload savedPayload;

    @BeforeEach
    void setUp() {
        pipeline = new Pipeline();
        pipeline.setId(1L);

        validRequest = new PayloadRequest("{\"orderId\":\"123\"}", "JSON");

        savedPayload = Payload.builder()
                .id(10L)
                .rawContent("{\"orderId\":\"123\"}")
                .format(DataFormat.JSON)
                .status(PayloadStatus.VALIDATED)
                .receivedAt(LocalDateTime.now())
                .pipeline(pipeline)
                .build();
    }

    // =========================================================================
    // receivePayload()
    // =========================================================================

    @Nested
    @DisplayName("receivePayload()")
    class ReceivePayload {

        @Test
        @DisplayName("accepts payload and saves with VALIDATED when no fields defined")
        void receivePayload_noFields_skipsValidation() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(payloadRepository.save(any())).thenReturn(savedPayload);

            PayloadResponse result = payloadService.receivePayload(1L, validRequest);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.status()).isEqualTo(PayloadStatus.VALIDATED);
            verify(structuralValidatorService, never()).validate(any(), any());
        }

        @Test
        @DisplayName("accepts payload and saves with VALIDATED when schema passes")
        void receivePayload_withFields_validationPasses() {
            PipelineField field = new PipelineField();
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));
            doNothing().when(structuralValidatorService).validate(any(), any());
            when(payloadRepository.save(any())).thenReturn(savedPayload);

            PayloadResponse result = payloadService.receivePayload(1L, validRequest);

            assertThat(result.status()).isEqualTo(PayloadStatus.VALIDATED);
            verify(structuralValidatorService).validate(eq("{\"orderId\":\"123\"}"), any());
        }

        @Test
        @DisplayName("persists FAILED payload and rethrows PayloadValidationException on violation")
        void receivePayload_withFields_validationFails() {
            PipelineField field = new PipelineField();
            List<FieldViolation> violations = List.of(
                    new FieldViolation("orderId", "MISSING_FIELD", "Field is required"));

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));
            doThrow(new PayloadValidationException(violations))
                    .when(structuralValidatorService).validate(any(), any());

            Payload failedPayload = Payload.builder()
                    .id(11L).rawContent("{}")
                    .format(DataFormat.JSON).status(PayloadStatus.FAILED)
                    .receivedAt(LocalDateTime.now()).pipeline(pipeline).build();
            
            // Stubbing requires flexibility because save() is called twice with different entity states
            when(payloadRepository.save(any())).thenReturn(failedPayload);

            assertThatThrownBy(() -> payloadService.receivePayload(1L, validRequest))
                    .isInstanceOf(PayloadValidationException.class)
                    .satisfies(ex -> {
                        PayloadValidationException pve = (PayloadValidationException) ex;
                        assertThat(pve.getViolations()).hasSize(1);
                        assertThat(pve.getViolations().get(0).fieldPath()).isEqualTo("orderId");
                    });

            // ✅ Fix: Capturing across multiple invocations (Initial Save + Failed Save)
            ArgumentCaptor<Payload> captor = ArgumentCaptor.forClass(Payload.class);
            verify(payloadRepository, times(2)).save(captor.capture());
            
            // The final status update should be FAILED
            assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(PayloadStatus.FAILED);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void receivePayload_pipelineNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> payloadService.receivePayload(99L, validRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(payloadRepository, never()).save(any());
        }

        @Test
        @DisplayName("persisted payload has correct format derived from request")
        void receivePayload_correctFormatPersisted() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(payloadRepository.save(any())).thenReturn(savedPayload);

            payloadService.receivePayload(1L, validRequest);

            // ✅ Fix: Verify both saves and inspect the initial properties
            ArgumentCaptor<Payload> captor = ArgumentCaptor.forClass(Payload.class);
            verify(payloadRepository, times(2)).save(captor.capture());
            
            assertThat(captor.getAllValues().get(0).getFormat()).isEqualTo(DataFormat.JSON);
            assertThat(captor.getAllValues().get(0).getPipeline()).isEqualTo(pipeline);
        }
    }

    // =========================================================================
    // getPayloadsByPipeline()
    // =========================================================================

    @Nested
    @DisplayName("getPayloadsByPipeline()")
    class GetPayloadsByPipeline {

        @Test
        @DisplayName("returns all payloads mapped to DTOs")
        void getPayloadsByPipeline_returnsList() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(payloadRepository.findAllByPipelineId(1L)).thenReturn(List.of(savedPayload));

            List<PayloadResponse> result = payloadService.getPayloadsByPipeline(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(10L);
            assertThat(result.get(0).pipelineId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty list when pipeline has no payloads")
        void getPayloadsByPipeline_empty() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(payloadRepository.findAllByPipelineId(1L)).thenReturn(List.of());

            assertThat(payloadService.getPayloadsByPipeline(1L)).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void getPayloadsByPipeline_pipelineNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> payloadService.getPayloadsByPipeline(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getPayloadById()
    // =========================================================================

    @Nested
    @DisplayName("getPayloadById()")
    class GetPayloadById {

        @Test
        @DisplayName("returns DTO when payload found")
        void getPayloadById_found() {
            when(payloadRepository.findById(10L)).thenReturn(Optional.of(savedPayload));

            PayloadResponse result = payloadService.getPayloadById(10L);

            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.status()).isEqualTo(PayloadStatus.VALIDATED);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when payload not found")
        void getPayloadById_notFound() {
            when(payloadRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> payloadService.getPayloadById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }
}