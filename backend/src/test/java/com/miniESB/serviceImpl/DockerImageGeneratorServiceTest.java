package com.miniESB.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.docker.BuildLogEntryResponse;
import com.miniESB.dto.docker.DockerImageBuildResponse;
import com.miniESB.dto.docker.DockerImageResponse;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.ImageVersionService;
import com.miniESB.service.docker.CommandExecutor;
import com.miniESB.service.docker.CommandResult;
import com.miniESB.service.impl.DockerBuildArtifactGenerator;
import com.miniESB.service.impl.DockerImageGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Complément de {@code DockerImageGeneratorServiceTest} : couvre les chemins réels
 * du service (pas seulement le contrat des exceptions) grâce au mock de
 * {@link CommandExecutor}, introduit par le refactoring Dependency Inversion.
 *
 * <p>NE couvre PAS {@code generateImageWithSse(...)} (Async + SseEmitter) —
 * à traiter séparément.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerImageGeneratorService — real service paths")
class DockerImageGeneratorServiceRealPathsTest {

    @Mock private PipelineRepository pipelineRepository;
    @Mock private DockerImageRepository dockerImageRepository;
    @Mock private BuildLogEntryRepository buildLogEntryRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ImageVersionService imageVersionService;
    @Mock private DockerBuildArtifactGenerator artifactGenerator;
    @Mock private CommandExecutor commandExecutor;

    @InjectMocks
    private DockerImageGeneratorService service;

    private Pipeline validatedPipeline;
    private DockerImage existingImage;

    @BeforeEach
    void setUp() {
        validatedPipeline = Pipeline.builder()
                .id(42L)
                .status(PipelineStatus.VALIDATED)
                .version("1.0.0")
                .build();

        existingImage = DockerImage.builder()
                .id(10L)
                .pipeline(validatedPipeline)
                .status(ImageStatus.PENDING)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  generateImage() — chemin succès complet
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateImage — success path")
    class GenerateImageSuccess {

        @Test
        @DisplayName("returns SUCCESS response and persists DockerImage as SUCCESS")
        void generateImage_happyPath_returnsSuccessAndSavesImage() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");

            // "docker info" (checkDockerDaemon) puis "docker build" (buildDockerImage)
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(0, "OK"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("build")), any()))
                    .thenReturn(new CommandResult(0, "Successfully built abc123"));
            // "docker inspect" (getImageSize)
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("inspect"))))
                    .thenReturn(new CommandResult(0, "123456"));

            DockerImageBuildResponse response = service.generateImage(42L);

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.tag()).isEqualTo("1.0.1");

            verify(dockerImageRepository, atLeastOnce()).save(argThat(
                    img -> img.getStatus() == ImageStatus.SUCCESS));
            verify(imageVersionService).applyNextVersion(existingImage, "1.0.1", "1.0.0");
        }

        @Test
        @DisplayName("creates a new DockerImage when none exists yet for the pipeline")
        void generateImage_noExistingImage_createsNewOne() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.empty());
            when(imageVersionService.computeNextTag(any(), eq(validatedPipeline))).thenReturn("1.0.0");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(0, "OK"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("build")), any()))
                    .thenReturn(new CommandResult(0, "built"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("inspect"))))
                    .thenReturn(new CommandResult(0, ""));

            service.generateImage(42L);

            verify(dockerImageRepository, atLeastOnce()).save(argThat(
                    img -> img.getPipeline().equals(validatedPipeline)));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  generateImage() — daemon Docker injoignable (vrai appel service)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateImage — Docker daemon unreachable (real call)")
    class GenerateImageDaemonUnreachable {

        @Test
        @DisplayName("throws DockerDaemonException when 'docker info' exits non-zero")
        void generateImage_dockerInfoFails_throwsDaemonException() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(1, "Cannot connect to the Docker daemon"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerDaemonException.class);
        }

        @Test
        @DisplayName("marks DockerImage as FAILED, pipeline stays VALIDATED, when daemon unreachable")
        void generateImage_daemonUnreachable_imageFailedPipelineUnchanged() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(1, "daemon down"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerDaemonException.class);

            verify(dockerImageRepository, atLeastOnce()).save(argThat(
                    img -> img.getStatus() == ImageStatus.FAILED));
            verify(pipelineRepository, never()).save(any());
            assertThat(validatedPipeline.getStatus()).isEqualTo(PipelineStatus.VALIDATED);
        }

        @Test
        @DisplayName("throws DockerDaemonException when 'docker info' throws IOException")
        void generateImage_dockerInfoIoException_throwsDaemonException() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenThrow(new java.io.IOException("socket not found"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerDaemonException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  generateImage() — build Docker échoué (vrai appel service)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateImage — Docker build failed (real call)")
    class GenerateImageBuildFailed {

        @Test
        @DisplayName("throws DockerBuildException with exit code and log when 'docker build' fails")
        void generateImage_dockerBuildFails_throwsBuildException() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(0, "OK"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("build")), any()))
                    .thenReturn(new CommandResult(1, "Error: Dockerfile parse error"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerBuildException.class)
                    .satisfies(ex -> {
                        DockerBuildException dbe = (DockerBuildException) ex;
                        assertThat(dbe.getExitCode()).isEqualTo(1);
                        assertThat(dbe.getBuildLog()).contains("Dockerfile parse error");
                    });
        }

        @Test
        @DisplayName("marks DockerImage as FAILED, pipeline stays VALIDATED, when build fails")
        void generateImage_buildFailed_imageFailedPipelineUnchanged() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(0, "OK"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("build")), any()))
                    .thenReturn(new CommandResult(1, "boom"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerBuildException.class);

            verify(dockerImageRepository, atLeastOnce()).save(argThat(
                    img -> img.getStatus() == ImageStatus.FAILED));
            verify(pipelineRepository, never()).save(any());
            assertThat(validatedPipeline.getStatus()).isEqualTo(PipelineStatus.VALIDATED);
        }

        @Test
        @DisplayName("never calls imageVersionService.applyNextVersion when build fails")
        void generateImage_buildFailed_neverAppliesVersion() throws Exception {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(existingImage));
            when(imageVersionService.computeNextTag(existingImage, validatedPipeline)).thenReturn("1.0.1");
            when(commandExecutor.run(eq(List.of("docker", "info"))))
                    .thenReturn(new CommandResult(0, "OK"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("build")), any()))
                    .thenReturn(new CommandResult(1, "boom"));

            assertThatThrownBy(() -> service.generateImage(42L))
                    .isInstanceOf(DockerBuildException.class);

            verify(imageVersionService, never()).applyNextVersion(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  exportImage()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("exportImage")
    class ExportImage {

        @Test
        @DisplayName("returns raw bytes via 'docker save' when image status is SUCCESS")
        void exportImage_successStatus_returnsBytes() throws Exception {
            DockerImage successImage = DockerImage.builder()
                    .id(10L).pipeline(validatedPipeline)
                    .status(ImageStatus.SUCCESS)
                    .imageName("pipeline-42").tag("1.0.0")
                    .build();
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(successImage));
            byte[] expected = {1, 2, 3};
            when(commandExecutor.runForBytes(List.of("docker", "save", "pipeline-42:1.0.0")))
                    .thenReturn(expected);

            byte[] result = service.exportImage(42L);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no image exists for pipeline")
        void exportImage_noImage_throwsResourceNotFound() {
            when(dockerImageRepository.findByPipelineId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.exportImage(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when image status is not SUCCESS")
        void exportImage_notSuccessStatus_throwsIllegalState() {
            DockerImage buildingImage = DockerImage.builder()
                    .id(10L).pipeline(validatedPipeline)
                    .status(ImageStatus.BUILDING)
                    .build();
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(buildingImage));

            assertThatThrownBy(() -> service.exportImage(42L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUILDING");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getCurrentVersionInfo() / getImageInfo()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCurrentVersionInfo / getImageInfo")
    class VersionAndImageInfo {

        @Test
        @DisplayName("getCurrentVersionInfo returns map with tag, pipelineVersion, patch")
        void getCurrentVersionInfo_returnsExpectedFields() {
            DockerImage image = DockerImage.builder()
                    .id(10L).pipeline(validatedPipeline)
                    .tag("1.0.3").versionPatch(3).lastPipelineVersion("1.0.0")
                    .build();
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(image));

            var result = service.getCurrentVersionInfo(42L);

            assertThat(result)
                    .containsEntry("pipelineId", 42L)
                    .containsEntry("currentTag", "1.0.3")
                    .containsEntry("patch", 3)
                    .containsEntry("lastPipelineVersion", "1.0.0");
        }

        @Test
        @DisplayName("getCurrentVersionInfo throws ResourceNotFoundException when no image")
        void getCurrentVersionInfo_noImage_throwsResourceNotFound() {
            when(dockerImageRepository.findByPipelineId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrentVersionInfo(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getImageInfo maps DockerImage entity to DockerImageResponse")
        void getImageInfo_mapsToResponse() {
            DockerImage image = DockerImage.builder()
                    .id(10L).pipeline(validatedPipeline)
                    .imageName("pipeline-42").tag("1.0.0")
                    .status(ImageStatus.SUCCESS).sizeBytes(1024L)
                    .builtAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                    .build();
            when(dockerImageRepository.findByPipelineId(42L)).thenReturn(Optional.of(image));

            DockerImageResponse response = service.getImageInfo(42L);

            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.imageName()).isEqualTo("pipeline-42");
            assertThat(response.tag()).isEqualTo("1.0.0");
            assertThat(response.status()).isEqualTo(ImageStatus.SUCCESS);
            assertThat(response.sizeBytes()).isEqualTo(1024L);
            assertThat(response.pipelineId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("getImageInfo throws ResourceNotFoundException when no image for pipeline")
        void getImageInfo_noImage_throwsResourceNotFound() {
            when(dockerImageRepository.findByPipelineId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getImageInfo(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  assertPipelineValidated()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("assertPipelineValidated")
    class AssertPipelineValidated {

        @Test
        @DisplayName("does not throw when pipeline is VALIDATED")
        void assertPipelineValidated_validatedPipeline_doesNotThrow() {
            when(pipelineRepository.findById(42L)).thenReturn(Optional.of(validatedPipeline));

            assertThatCode(() -> service.assertPipelineValidated(42L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws IllegalStateException when pipeline is not VALIDATED")
        void assertPipelineValidated_draftPipeline_throwsIllegalState() {
            Pipeline draft = Pipeline.builder().id(1L).status(PipelineStatus.DRAFT).build();
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(draft));

            assertThatThrownBy(() -> service.assertPipelineValidated(1L))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void assertPipelineValidated_unknownPipeline_throwsResourceNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assertPipelineValidated(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getVersionHistory()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getVersionHistory")
    class VersionHistory {

        @Test
        @DisplayName("maps BuildLogEntry list to responses, most recent first, with computed duration")
        void getVersionHistory_mapsEntriesWithDuration() {
            LocalDateTime start = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2026, 7, 1, 10, 2);
            BuildLogEntry entry = BuildLogEntry.builder()
                    .id(1L).pipelineId(42L).version("1.0.0")
                    .status(ImageStatus.SUCCESS)
                    .startTime(start).endTime(end)
                    .build();
            when(buildLogEntryRepository.findByPipelineIdOrderByStartTimeDesc(42L))
                    .thenReturn(List.of(entry));

            List<BuildLogEntryResponse> history = service.getVersionHistory(42L);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).durationSeconds()).isEqualTo(120L);
        }

        @Test
        @DisplayName("returns empty list when no build history exists")
        void getVersionHistory_noEntries_returnsEmptyList() {
            when(buildLogEntryRepository.findByPipelineIdOrderByStartTimeDesc(99L))
                    .thenReturn(List.of());

            assertThat(service.getVersionHistory(99L)).isEmpty();
        }

        @Test
        @DisplayName("duration is null when endTime is missing (build still running)")
        void getVersionHistory_missingEndTime_durationIsNull() {
            BuildLogEntry entry = BuildLogEntry.builder()
                    .id(2L).pipelineId(42L).version("1.0.1")
                    .status(ImageStatus.BUILDING)
                    .startTime(LocalDateTime.now()).endTime(null)
                    .build();
            when(buildLogEntryRepository.findByPipelineIdOrderByStartTimeDesc(42L))
                    .thenReturn(List.of(entry));

            List<BuildLogEntryResponse> history = service.getVersionHistory(42L);

            assertThat(history.get(0).durationSeconds()).isNull();
        }
    }
}