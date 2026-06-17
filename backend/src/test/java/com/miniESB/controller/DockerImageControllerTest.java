package com.miniESB.controller;

import com.miniESB.controller.DockerImageController;
import com.miniESB.dto.docker.DockerImageBuildResponse;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.impl.DockerImageGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DockerImageController} — gestion d'erreurs.
 *
 * <p>Vérifie que le contrôleur :
 * <ul>
 *   <li>retourne HTTP 503 + corps enrichi sur {@link DockerDaemonException}</li>
 *   <li>retourne HTTP 422 + log complet sur {@link DockerBuildException}</li>
 *   <li>retourne HTTP 422 sur pipeline non VALIDATED</li>
 *   <li>retourne HTTP 200 sur succès</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerImageController — error handling")
class DockerImageControllerTest {

    @Mock
    private DockerImageGeneratorService generatorService;

    @InjectMocks
    private DockerImageController controller;

    private static final Long PIPELINE_ID = 42L;

    // ══════════════════════════════════════════════════════════════════════════
    //  Succès
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful build")
    class SuccessfulBuild {

        @Test
        @DisplayName("returns 200 with DockerImageBuildResponse on success")
        void generate_success_returns200() throws Exception {
            DockerImageBuildResponse expected = new DockerImageBuildResponse(
                    10L, "pipeline-42", "1.0.0", "SUCCESS", "Image built successfully");
            when(generatorService.generateImage(PIPELINE_ID)).thenReturn(expected);

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Daemon injoignable
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Docker daemon unreachable")
    class DaemonUnreachable {

        @Test
        @DisplayName("returns 503 when DockerDaemonException is thrown")
        void generate_daemonException_returns503() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerDaemonException(
                            "Docker daemon is not reachable (docker info exited with code 1)",
                            "Cannot connect to the Docker daemon at unix:///var/run/docker.sock"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("response body contains errorType DAEMON_UNREACHABLE")
        void generate_daemonException_bodyHasErrorType() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerDaemonException(
                            "Docker daemon is not reachable",
                            "Cannot connect to the Docker daemon at unix:///var/run/docker.sock"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("errorType");
            assertThat(body.get("errorType")).isEqualTo("DAEMON_UNREACHABLE");
        }

        @Test
        @DisplayName("response body contains technicalDetail")
        void generate_daemonException_bodyHasTechnicalDetail() throws Exception {
            String detail = "Cannot connect to the Docker daemon at unix:///var/run/docker.sock";
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerDaemonException("Docker daemon is not reachable", detail));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("technicalDetail");
            assertThat(body.get("technicalDetail").toString()).contains("docker.sock");
        }

        @Test
        @DisplayName("response body contains hint for the developer")
        void generate_daemonException_bodyHasHint() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerDaemonException("msg", "detail"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("hint");
            assertThat(body.get("hint").toString()).containsIgnoringCase("docker daemon");
        }

        @Test
        @DisplayName("response body contains timestamp")
        void generate_daemonException_bodyHasTimestamp() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerDaemonException("msg", "detail"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("timestamp");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Build échoué
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Docker build failed")
    class BuildFailed {

        private static final String BUILD_LOG =
                "Step 1/3 : FROM mini-esb-backend:latest\n" +
                        "Error response from daemon: pull access denied\n" +
                        "The command returned a non-zero code: 1";

        @Test
        @DisplayName("returns 422 when DockerBuildException is thrown")
        void generate_buildException_returns422() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException(
                            "docker build failed with exit code: 1", 1, BUILD_LOG));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("response body contains errorType BUILD_ERROR")
        void generate_buildException_bodyHasErrorType() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException("msg", 1, BUILD_LOG));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("errorType")).isEqualTo("BUILD_ERROR");
        }

        @Test
        @DisplayName("response body contains exitCode")
        void generate_buildException_bodyHasExitCode() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException("msg", 1, BUILD_LOG));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("exitCode")).isEqualTo(1);
        }

        @Test
        @DisplayName("response body contains full buildLog")
        void generate_buildException_bodyHasBuildLog() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException("msg", 1, BUILD_LOG));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("buildLog").toString())
                    .contains("Step 1/3")
                    .contains("pull access denied");
        }

        @Test
        @DisplayName("response body contains timestamp")
        void generate_buildException_bodyHasTimestamp() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException("msg", 1, BUILD_LOG));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("buildLog key absent when buildLog is null")
        void generate_buildException_noBuildLogKey_whenLogIsNull() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new DockerBuildException("msg", 1, null));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).doesNotContainKey("buildLog");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Pipeline non VALIDATED
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pipeline not VALIDATED")
    class PipelineNotValidated {

        @Test
        @DisplayName("returns 422 when pipeline is not in VALIDATED status")
        void generate_notValidated_returns422() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new IllegalStateException(
                            "Pipeline must be VALIDATED to generate an image — current status: DRAFT"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("response body contains errorType PIPELINE_NOT_VALIDATED")
        void generate_notValidated_bodyHasErrorType() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new IllegalStateException(
                            "Pipeline must be VALIDATED — current status: DRAFT"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("errorType")).isEqualTo("PIPELINE_NOT_VALIDATED");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Fallback 500
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unexpected error fallback")
    class UnexpectedError {

        @Test
        @DisplayName("returns 500 on unexpected exception")
        void generate_unexpectedException_returns500() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new RuntimeException("Unexpected IO failure"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("response body contains errorType BUILD_FAILED on unexpected error")
        void generate_unexpectedException_bodyHasErrorType() throws Exception {
            when(generatorService.generateImage(PIPELINE_ID))
                    .thenThrow(new RuntimeException("Unexpected IO failure"));

            ResponseEntity<?> response = controller.generate(PIPELINE_ID);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("errorType")).isEqualTo("BUILD_FAILED");
        }
    }
}