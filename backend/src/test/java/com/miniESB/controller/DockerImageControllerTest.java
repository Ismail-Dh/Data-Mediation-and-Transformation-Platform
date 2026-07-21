package com.miniESB.controller;

import com.miniESB.dto.docker.*;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.ImageVersionService;
import com.miniESB.service.impl.DockerImageGeneratorService;
import com.miniESB.service.impl.DockerImagePushService;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DockerImageController} — tous les endpoints.
 *
 * <p>Complète le fichier existant (gestion d'erreurs de {@code generate()}) en
 * couvrant les autres routes : {@code getImageInfo}, {@code download},
 * {@code getCurrentVersion}, {@code getVersionHistory} et {@code push}.</p>
 *
 * <p><strong>En attente</strong> : {@code bumpVersion()} nécessite
 * {@code VersionBumpRequest}/{@code VersionBumpResponse}, non fournis.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerImageController — error handling")
class DockerImageControllerTest {

    @Mock
    private DockerImageGeneratorService generatorService;
    @Mock
    private ImageVersionService imageVersionService;
    @Mock
    private DockerImagePushService pushService;

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

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /version/bump
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bumpVersion")
    class BumpVersion {

        @Test
        @DisplayName("delegates a MINOR bump to imageVersionService and returns its response")
        void bumpVersion_minor_delegatesAndReturnsResponse() {
            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, null);
            VersionBumpResponse expected = new VersionBumpResponse(
                    PIPELINE_ID, "1.2", "1.3", "Version bumped to 1.3");
            when(imageVersionService.bumpVersion(PIPELINE_ID, request)).thenReturn(expected);

            ResponseEntity<VersionBumpResponse> response = controller.bumpVersion(PIPELINE_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("delegates a MAJOR bump to imageVersionService and returns its response")
        void bumpVersion_major_delegatesAndReturnsResponse() {
            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MAJOR, null);
            VersionBumpResponse expected = new VersionBumpResponse(
                    PIPELINE_ID, "1.2", "2.0", "Version bumped to 2.0");
            when(imageVersionService.bumpVersion(PIPELINE_ID, request)).thenReturn(expected);

            ResponseEntity<VersionBumpResponse> response = controller.bumpVersion(PIPELINE_ID, request);

            assertThat(response.getBody().newVersion()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("passes an explicit targetVersion override through to the service unchanged")
        void bumpVersion_withTargetVersionOverride_passesThroughUnchanged() {
            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MAJOR, "5.0");
            VersionBumpResponse expected = new VersionBumpResponse(
                    PIPELINE_ID, "1.2", "5.0", "Version bumped to 5.0");
            when(imageVersionService.bumpVersion(PIPELINE_ID, request)).thenReturn(expected);

            controller.bumpVersion(PIPELINE_ID, request);

            verify(imageVersionService).bumpVersion(eq(PIPELINE_ID), argThat(
                    r -> "5.0".equals(r.targetVersion()) && r.type() == VersionBumpRequest.BumpType.MAJOR));
        }

        @Test
        @DisplayName("propagates the exception when the service rejects the request (no local error handling)")
        void bumpVersion_serviceThrows_propagatesException() {
            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, "not-a-version");
            when(imageVersionService.bumpVersion(eq(PIPELINE_ID), any()))
                    .thenThrow(new IllegalArgumentException("Invalid target version format"));

            assertThatThrownBy(() -> controller.bumpVersion(PIPELINE_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET / — getImageInfo
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getImageInfo")
    class GetImageInfo {

        @Test
        @DisplayName("delegates to generatorService and returns its response as-is")
        void getImageInfo_delegatesAndReturnsResponse() {
            DockerImageResponse expected = mock(DockerImageResponse.class);
            when(generatorService.getImageInfo(PIPELINE_ID)).thenReturn(expected);

            ResponseEntity<DockerImageResponse> response = controller.getImageInfo(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when no image exists (handled globally)")
        void getImageInfo_noImage_propagatesException() {
            when(generatorService.getImageInfo(99L))
                    .thenThrow(new ResourceNotFoundException("No image found for pipeline id=99"));

            assertThatThrownBy(() -> controller.getImageInfo(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /download
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        @DisplayName("returns the image bytes with correct Content-Disposition and Content-Type")
        void download_success_returnsBytesWithCorrectHeaders() throws Exception {
            byte[] imageBytes = {1, 2, 3, 4};
            when(generatorService.exportImage(PIPELINE_ID)).thenReturn(imageBytes);

            ResponseEntity<byte[]> response = controller.download(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(imageBytes);
            assertThat(response.getHeaders().getContentDisposition().toString())
                    .contains("attachment")
                    .contains("pipeline-42.tar");
            assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        }

        @Test
        @DisplayName("propagates the exception when export fails (no local error handling)")
        void download_exportFails_propagatesException() throws Exception {
            when(generatorService.exportImage(99L))
                    .thenThrow(new ResourceNotFoundException("No image found for pipeline id=99"));

            assertThatThrownBy(() -> controller.download(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /version — getCurrentVersion
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCurrentVersion")
    class GetCurrentVersion {

        @Test
        @DisplayName("delegates to generatorService.getCurrentVersionInfo and returns it as-is")
        void getCurrentVersion_delegatesAndReturnsMap() {
            Map<String, Object> expected = Map.of("pipelineId", 42L, "currentTag", "1.0.3");
            when(generatorService.getCurrentVersionInfo(PIPELINE_ID)).thenReturn(expected);

            ResponseEntity<Map<String, Object>> response = controller.getCurrentVersion(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /versions — getVersionHistory
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getVersionHistory")
    class GetVersionHistory {

        @Test
        @DisplayName("delegates to generatorService.getVersionHistory and returns it as-is")
        void getVersionHistory_delegatesAndReturnsList() {
            List<BuildLogEntryResponse> expected = List.of(mock(BuildLogEntryResponse.class));
            when(generatorService.getVersionHistory(PIPELINE_ID)).thenReturn(expected);

            ResponseEntity<List<BuildLogEntryResponse>> response = controller.getVersionHistory(PIPELINE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns an empty list when the pipeline has no build history")
        void getVersionHistory_noHistory_returnsEmptyList() {
            when(generatorService.getVersionHistory(PIPELINE_ID)).thenReturn(List.of());

            ResponseEntity<List<BuildLogEntryResponse>> response = controller.getVersionHistory(PIPELINE_ID);

            assertThat(response.getBody()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /push
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("push")
    class Push {

        @Test
        @DisplayName("returns 200 with PushImageResponse on success")
        void push_success_returns200() throws Exception {
            PushImageRequest request = new PushImageRequest(5L);
            PushImageResponse expected = new PushImageResponse(
                    PIPELINE_ID, 5L, "My Registry", "myregistry.io/pipeline-42:1.0.0",
                    "SUCCESS", "Image pushed successfully to My Registry");
            when(pushService.pushImage(PIPELINE_ID, 5L)).thenReturn(expected);

            ResponseEntity<?> response = controller.push(PIPELINE_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns 422 with errorType IMAGE_NOT_READY when image is not SUCCESS")
        void push_imageNotReady_returns422() throws Exception {
            PushImageRequest request = new PushImageRequest(5L);
            when(pushService.pushImage(PIPELINE_ID, 5L))
                    .thenThrow(new IllegalStateException("Image must be SUCCESS before pushing — current: BUILDING"));

            ResponseEntity<?> response = controller.push(PIPELINE_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("errorType")).isEqualTo("IMAGE_NOT_READY");
        }

        @Test
        @DisplayName("returns 500 with errorType PUSH_FAILED on unexpected error")
        void push_unexpectedError_returns500() throws Exception {
            PushImageRequest request = new PushImageRequest(5L);
            when(pushService.pushImage(PIPELINE_ID, 5L))
                    .thenThrow(new RuntimeException("docker push failed with exit code: 1"));

            ResponseEntity<?> response = controller.push(PIPELINE_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("errorType")).isEqualTo("PUSH_FAILED");
            assertThat(body.get("error").toString()).contains("docker push failed");
        }

        @Test
        @DisplayName("returns 404-mapped ResourceNotFoundException as a 500 (no explicit 404 handling in push())")
        void push_registryNotFound_isCaughtByGenericHandler() throws Exception {
            PushImageRequest request = new PushImageRequest(99L);
            when(pushService.pushImage(PIPELINE_ID, 99L))
                    .thenThrow(new ResourceNotFoundException("Registry not found: 99"));

            ResponseEntity<?> response = controller.push(PIPELINE_ID, request);

            // Note : ResourceNotFoundException est une RuntimeException générique ici,
            // pas gérée explicitement dans push() → tombe dans le catch (Exception e) → 500.
            // Si un vrai 404 est souhaité pour ce cas, il faudrait un catch dédié.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}