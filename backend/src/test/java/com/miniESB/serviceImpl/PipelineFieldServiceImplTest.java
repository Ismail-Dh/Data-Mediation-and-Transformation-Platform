package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.FieldType;
import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.impl.PipelineFieldServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineFieldServiceImpl — unit tests")
class PipelineFieldServiceImplTest {

    @Mock private PipelineFieldRepository pipelineFieldRepository;
    @Mock private PipelineRepository pipelineRepository;

    @InjectMocks
    private PipelineFieldServiceImpl pipelineFieldService;

    private Pipeline pipeline;
    private PipelineField pipelineField;

    @BeforeEach
    void setUp() {
        pipeline = new Pipeline();
        pipeline.setId(1L);

        pipelineField = PipelineField.builder()
                .id(100L)
                .fieldPath("orderId")
                .fieldType(FieldType.STRING)
                .required(true)
                .nullable(false)
                .pipeline(pipeline)
                .build();
    }

    // =========================================================================
    // addField()
    // =========================================================================

    @Nested
    @DisplayName("addField()")
    class AddField {

        @Test
        @DisplayName("creates and saves field when fieldPath does not exist")
        void addField_success() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "orderId", FieldType.STRING, true, false);

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.existsByPipelineIdAndFieldPath(1L, "orderId"))
                    .thenReturn(false);
            when(pipelineFieldRepository.save(any(PipelineField.class)))
                    .thenReturn(pipelineField);

            PipelineFieldResponse result = pipelineFieldService.addField(1L, request);

            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.fieldPath()).isEqualTo("orderId");
            assertThat(result.fieldType()).isEqualTo(FieldType.STRING);
            assertThat(result.required()).isTrue();
            assertThat(result.nullable()).isFalse();

            ArgumentCaptor<PipelineField> captor = ArgumentCaptor.forClass(PipelineField.class);
            verify(pipelineFieldRepository).save(captor.capture());
            assertThat(captor.getValue().getPipeline()).isEqualTo(pipeline);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when fieldPath already exists")
        void addField_duplicateFieldPath_throwsException() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "orderId", FieldType.STRING, true, false);

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.existsByPipelineIdAndFieldPath(1L, "orderId"))
                    .thenReturn(true);

            assertThatThrownBy(() -> pipelineFieldService.addField(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(pipelineFieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void addField_pipelineNotFound() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "orderId", FieldType.STRING, true, false);

            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineFieldService.addField(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // =========================================================================
    // getFields()
    // =========================================================================

    @Nested
    @DisplayName("getFields()")
    class GetFields {

        @Test
        @DisplayName("returns all fields for a pipeline")
        void getFields_success() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L))
                    .thenReturn(List.of(pipelineField));

            List<PipelineFieldResponse> result = pipelineFieldService.getFields(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("returns empty list when pipeline has no fields")
        void getFields_empty() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());

            assertThat(pipelineFieldService.getFields(1L)).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void getFields_pipelineNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineFieldService.getFields(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // updateField()
    // =========================================================================

    @Nested
    @DisplayName("updateField()")
    class UpdateField {

        @Test
        @DisplayName("updates field successfully")
        void updateField_success() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "updatedPath", FieldType.INTEGER, false, true);

            when(pipelineFieldRepository.findById(100L)).thenReturn(Optional.of(pipelineField));
            when(pipelineFieldRepository.existsByPipelineIdAndFieldPath(1L, "updatedPath"))
                    .thenReturn(false);
            when(pipelineFieldRepository.save(any(PipelineField.class)))
                    .thenReturn(pipelineField);

            PipelineFieldResponse result = pipelineFieldService.updateField(1L, 100L, request);

            assertThat(result.fieldPath()).isEqualTo("updatedPath");
            assertThat(result.fieldType()).isEqualTo(FieldType.INTEGER);
            assertThat(result.required()).isFalse();
            assertThat(result.nullable()).isTrue();

            verify(pipelineFieldRepository).save(pipelineField);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when new fieldPath already exists")
        void updateField_duplicateFieldPath_throwsException() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "existingPath", FieldType.STRING, true, false);

            when(pipelineFieldRepository.findById(100L)).thenReturn(Optional.of(pipelineField));
            when(pipelineFieldRepository.existsByPipelineIdAndFieldPath(1L, "existingPath"))
                    .thenReturn(true);

            assertThatThrownBy(() -> pipelineFieldService.updateField(1L, 100L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when field not found")
        void updateField_fieldNotFound() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "path", FieldType.STRING, true, false);

            when(pipelineFieldRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineFieldService.updateField(1L, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when field does not belong to pipeline")
        void updateField_fieldWrongPipeline() {
            PipelineFieldRequest request = new PipelineFieldRequest(
                    "path", FieldType.STRING, true, false);

            Pipeline wrongPipeline = new Pipeline();
            wrongPipeline.setId(2L);
            pipelineField.setPipeline(wrongPipeline);

            when(pipelineFieldRepository.findById(100L)).thenReturn(Optional.of(pipelineField));

            assertThatThrownBy(() -> pipelineFieldService.updateField(1L, 100L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    // =========================================================================
    // deleteField()
    // =========================================================================

    @Nested
    @DisplayName("deleteField()")
    class DeleteField {

        @Test
        @DisplayName("deletes field successfully")
        void deleteField_success() {
            when(pipelineFieldRepository.findById(100L)).thenReturn(Optional.of(pipelineField));

            pipelineFieldService.deleteField(1L, 100L);

            verify(pipelineFieldRepository).delete(pipelineField);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when field not found")
        void deleteField_fieldNotFound() {
            when(pipelineFieldRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineFieldService.deleteField(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when field does not belong to pipeline")
        void deleteField_fieldWrongPipeline() {
            Pipeline wrongPipeline = new Pipeline();
            wrongPipeline.setId(2L);
            pipelineField.setPipeline(wrongPipeline);

            when(pipelineFieldRepository.findById(100L)).thenReturn(Optional.of(pipelineField));

            assertThatThrownBy(() -> pipelineFieldService.deleteField(1L, 100L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}