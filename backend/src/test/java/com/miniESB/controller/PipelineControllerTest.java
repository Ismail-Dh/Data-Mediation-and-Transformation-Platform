/*package com.miniESB.controller;


import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineController — unit tests")
class PipelineControllerTest {

    @Mock  private PipelineService pipelineService;
    @InjectMocks private PipelineController pipelineController;

    private final PipelineResponse pipelineDto = new PipelineResponse(
            1L, "My Pipeline", "1.0", LocalDateTime.now(),
            "JSON", "XML", "DRAFT", "dev1",
            10L, "REST Provider", "http://api.example.com");

    private Principal principal(String name) { return () -> name; }

    @Nested @DisplayName("createPipeline()")
    class CreatePipeline {

        @Test @DisplayName("returns 201 with created pipeline")
        void createPipeline_returns201() {
            CreatePipelineRequest req = new CreatePipelineRequest(
                    "My Pipeline", "1.0", "JSON", "XML", 10L);
            when(pipelineService.createPipeline(req, "dev1")).thenReturn(pipelineDto);

            ResponseEntity<PipelineResponse> response =
                    pipelineController.createPipeline(req, principal("dev1"));

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().name()).isEqualTo("My Pipeline");
            assertThat(response.getBody().createdBy()).isEqualTo("dev1");
        }

        @Test @DisplayName("propagates ResourceNotFoundException when provider not found")
        void createPipeline_providerNotFound() {
            CreatePipelineRequest req = new CreatePipelineRequest("P", "1.0", "JSON", "XML", 99L);
            when(pipelineService.createPipeline(req, "dev1"))
                    .thenThrow(new ResourceNotFoundException("Provider not found"));

            assertThatThrownBy(() -> pipelineController.createPipeline(req, principal("dev1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("updatePipeline()")
    class UpdatePipeline {

        @Test @DisplayName("returns 200 with updated pipeline")
        void updatePipeline_returns200() {
            UpdatePipelineRequest req = new UpdatePipelineRequest("Updated", null, null, null, null);
            when(pipelineService.updatePipeline(1L, req, "dev1")).thenReturn(pipelineDto);

            ResponseEntity<PipelineResponse> response =
                    pipelineController.updatePipeline(1L, req, principal("dev1"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void updatePipeline_notFound() {
            UpdatePipelineRequest req = new UpdatePipelineRequest(null, null, null, null, null);
            when(pipelineService.updatePipeline(eq(99L), any(), eq("dev1")))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> pipelineController.updatePipeline(99L, req, principal("dev1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("deletePipeline()")
    class DeletePipeline {

        @Test @DisplayName("returns 204 on successful delete")
        void deletePipeline_returns204() {
            doNothing().when(pipelineService).deletePipeline(1L, "dev1");

            ResponseEntity<Void> response =
                    pipelineController.deletePipeline(1L, principal("dev1"));

            assertThat(response.getStatusCode().value()).isEqualTo(204);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void deletePipeline_notFound() {
            doThrow(new ResourceNotFoundException("Pipeline not found"))
                    .when(pipelineService).deletePipeline(99L, "dev1");

            assertThatThrownBy(() -> pipelineController.deletePipeline(99L, principal("dev1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("getPipelineById()")
    class GetPipelineById {

        @Test @DisplayName("returns 200 with pipeline")
        void getPipelineById_returns200() {
            when(pipelineService.getPipelineById(1L, "dev1")).thenReturn(pipelineDto);

            ResponseEntity<PipelineResponse> response =
                    pipelineController.getPipelineById(1L, principal("dev1"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(1L);
        }
    }

    @Nested @DisplayName("getMyPipelines()")
    class GetMyPipelines {

        @Test @DisplayName("returns 200 with list of own pipelines")
        void getMyPipelines_returns200() {
            when(pipelineService.getMyPipelines("dev1")).thenReturn(List.of(pipelineDto));

            ResponseEntity<List<PipelineResponse>> response =
                    pipelineController.getMyPipelines(principal("dev1"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested @DisplayName("getAllPipelines()")
    class GetAllPipelines {

        @Test @DisplayName("returns 200 with all pipelines")
        void getAllPipelines_returns200() {
            when(pipelineService.getAllPipelines()).thenReturn(List.of(pipelineDto));

            ResponseEntity<List<PipelineResponse>> response =
                    pipelineController.getAllPipelines();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }
    }
}*/

