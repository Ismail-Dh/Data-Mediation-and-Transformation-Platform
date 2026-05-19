package com.miniESB.controller;

import com.miniESB.domain.enums.FieldType;
import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.PipelineFieldService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineFieldController — unit tests")
class PipelineFieldControllerTest {

    @Mock  private PipelineFieldService pipelineFieldService;
    @InjectMocks private PipelineFieldController controller;

    private final PipelineFieldResponse fieldDto =
            new PipelineFieldResponse(10L, "order.id", FieldType.valueOf("STRING"), true, false);

    private final PipelineFieldRequest request =
            new PipelineFieldRequest("order.id", FieldType.valueOf("STRING"), true, false);

    @Nested
    @DisplayName("addField()")
    class AddField {

        @Test
        @DisplayName("returns 201 with created field")
        void addField_returns201() {
            when(pipelineFieldService.addField(1L, request)).thenReturn(fieldDto);

            ResponseEntity<PipelineFieldResponse> response = controller.addField(1L, request);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().fieldPath()).isEqualTo("order.id");
            assertThat(response.getBody().required()).isTrue();
        }

        @Test
        @DisplayName("propagates IllegalArgumentException when field path is duplicate")
        void addField_duplicatePath() {
            when(pipelineFieldService.addField(1L, request))
                    .thenThrow(new IllegalArgumentException("Field path 'order.id' already exists"));

            assertThatThrownBy(() -> controller.addField(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order.id");
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void addField_pipelineNotFound() {
            when(pipelineFieldService.addField(99L, request))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> controller.addField(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getFields()")
    class GetFields {

        @Test
        @DisplayName("returns 200 with all pipeline fields")
        void getFields_returns200() {
            when(pipelineFieldService.getFields(1L)).thenReturn(List.of(fieldDto));

            ResponseEntity<List<PipelineFieldResponse>> response = controller.getFields(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).fieldPath()).isEqualTo("order.id");
        }

        @Test
        @DisplayName("returns 200 with empty list when no fields defined")
        void getFields_empty() {
            when(pipelineFieldService.getFields(1L)).thenReturn(List.of());

            assertThat(controller.getFields(1L).getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateField()")
    class UpdateField {

        @Test
        @DisplayName("returns 200 with updated field")
        void updateField_returns200() {
            PipelineFieldRequest updatedReq =
                    new PipelineFieldRequest("order.amount", FieldType.valueOf("INTEGER"), true, false);
            PipelineFieldResponse updatedDto =
                    new PipelineFieldResponse(10L, "order.amount", FieldType.valueOf("INTEGER"), true, false);

            when(pipelineFieldService.updateField(1L, 10L, updatedReq)).thenReturn(updatedDto);

            ResponseEntity<PipelineFieldResponse> response =
                    controller.updateField(1L, 10L, updatedReq);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().fieldPath()).isEqualTo("order.amount");
        }

        @Test
        @DisplayName("propagates IllegalArgumentException on duplicate path")
        void updateField_duplicatePath() {
            PipelineFieldRequest updatedReq =
                    new PipelineFieldRequest("order.email", FieldType.valueOf("STRING"), false, true);
            when(pipelineFieldService.updateField(1L, 10L, updatedReq))
                    .thenThrow(new IllegalArgumentException("Field path 'order.email' already exists"));

            assertThatThrownBy(() -> controller.updateField(1L, 10L, updatedReq))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when field not found")
        void updateField_notFound() {
            when(pipelineFieldService.updateField(1L, 99L, request))
                    .thenThrow(new ResourceNotFoundException("PipelineField not found"));

            assertThatThrownBy(() -> controller.updateField(1L, 99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteField()")
    class DeleteField {

        @Test
        @DisplayName("returns 204 on successful delete")
        void deleteField_returns204() {
            doNothing().when(pipelineFieldService).deleteField(1L, 10L);

            ResponseEntity<Void> response = controller.deleteField(1L, 10L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            verify(pipelineFieldService).deleteField(1L, 10L);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when field not found")
        void deleteField_notFound() {
            doThrow(new ResourceNotFoundException("PipelineField not found"))
                    .when(pipelineFieldService).deleteField(1L, 99L);

            assertThatThrownBy(() -> controller.deleteField(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}