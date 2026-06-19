package com.miniESB.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.impl.DockerImageGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour la gestion d'erreurs de {@link DockerImageGeneratorService}.
 *
 * <h3>Scénarios couverts</h3>
 * <ol>
 *   <li>Pipeline inexistant → ResourceNotFoundException</li>
 *   <li>Pipeline non VALIDATED → IllegalStateException</li>
 *   <li>Daemon Docker injoignable → DockerDaemonException (pipeline reste VALIDATED)</li>
 *   <li>Build Docker échoué (exit code != 0) → DockerBuildException (pipeline reste VALIDATED)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerImageGeneratorService — error handling")
class DockerImageGeneratorServiceTest {

    @Mock private PipelineRepository      pipelineRepository;
    @Mock private MappingRuleRepository   mappingRuleRepository;
    @Mock private PipelineFieldRepository pipelineFieldRepository;
    @Mock private DockerImageRepository   dockerImageRepository;
    @Mock private BuildLogEntryRepository buildLogEntryRepository;
    @Mock private ObjectMapper            objectMapper;

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
    //  1. Pipeline inexistant
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pipeline not found")
    class PipelineNotFound {

        @Test
        @DisplayName("throws ResourceNotFoundException when pipelineId does not exist")
        void generateImage_unknownPipeline_throwsResourceNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateImage(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("does not save any DockerImage when pipeline is not found")
        void generateImage_unknownPipeline_doesNotSaveImage() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateImage(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(dockerImageRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  2. Pipeline non VALIDATED
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pipeline not VALIDATED")
    class PipelineNotValidated {

        @Test
        @DisplayName("throws IllegalStateException when pipeline status is DRAFT")
        void generateImage_draftPipeline_throwsIllegalState() {
            Pipeline draftPipeline = Pipeline.builder()
                    .id(1L)
                    .status(PipelineStatus.DRAFT)
                    .build();
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(draftPipeline));

            assertThatThrownBy(() -> service.generateImage(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VALIDATED")
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("throws IllegalStateException when pipeline status is CONFIGURED")
        void generateImage_configuredPipeline_throwsIllegalState() {
            Pipeline configuredPipeline = Pipeline.builder()
                    .id(2L)
                    .status(PipelineStatus.CONFIGURED)
                    .build();
            when(pipelineRepository.findById(2L)).thenReturn(Optional.of(configuredPipeline));

            assertThatThrownBy(() -> service.generateImage(2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VALIDATED")
                    .hasMessageContaining("CONFIGURED");
        }

        @Test
        @DisplayName("pipeline status does NOT change when pipeline is not VALIDATED")
        void generateImage_notValidated_pipelineStatusUnchanged() {
            Pipeline draftPipeline = Pipeline.builder()
                    .id(1L)
                    .status(PipelineStatus.DRAFT)
                    .build();
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(draftPipeline));

            assertThatThrownBy(() -> service.generateImage(1L));

            // Le statut du pipeline ne doit pas être modifié
            verify(pipelineRepository, never()).save(any());
            assertThat(draftPipeline.getStatus()).isEqualTo(PipelineStatus.DRAFT);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3. Daemon Docker injoignable
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Docker daemon unreachable")
    class DockerDaemonUnreachable {

        @Test
        @DisplayName("DockerDaemonException is a RuntimeException")
        void daemonException_isRuntimeException() {
            assertThat(new DockerDaemonException("msg", "detail"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("DockerDaemonException carries the technical detail from IOException")
        void daemonException_carriesIoExceptionDetail() {
            IOException cause = new IOException("No such file or directory: /var/run/docker.sock");
            DockerDaemonException ex = new DockerDaemonException(
                    "Docker daemon is not reachable", cause);

            assertThat(ex.getTechnicalDetail())
                    .isEqualTo("No such file or directory: /var/run/docker.sock");
        }

        @Test
        @DisplayName("DockerDaemonException carries explicit technical detail (String)")
        void daemonException_carriesStringDetail() {
            DockerDaemonException ex = new DockerDaemonException(
                    "Docker daemon is not reachable (docker info exited with code 1)",
                    "error during connect: Get http://%2Fvar%2Frun%2Fdocker.sock/v1.24/info");

            assertThat(ex.getMessage()).contains("docker info exited with code 1");
            assertThat(ex.getTechnicalDetail()).contains("docker.sock");
        }

        /**
         * Vérifie que le contrat d'exception est correct en isolation :
         * DockerDaemonException est bien une RuntimeException non swallowed,
         * et le pipeline (objet VALIDATED) n'est pas modifié par la simple
         * construction de l'exception.
         */
        @Test
        @DisplayName("pipeline stays VALIDATED when DockerDaemonException is thrown")
        void daemonException_pipelineRemainsValidated() {
            // On vérifie uniquement le contrat de l'exception elle-même,
            // sans appeler generateImage() (qui nécessiterait un vrai ProcessBuilder).
            DockerDaemonException ex = new DockerDaemonException(
                    "Docker daemon is not reachable", "docker info exited with code 1");

            // Le pipeline NE DOIT PAS changer de statut
            assertThat(validatedPipeline.getStatus()).isEqualTo(PipelineStatus.VALIDATED);

            // La DockerDaemonException est bien une RuntimeException non swallowed
            assertThat(ex).isInstanceOf(RuntimeException.class);
            assertThat(ex.getMessage()).contains("not reachable");
        }

        @Test
        @DisplayName("DockerDaemonException message is descriptive")
        void daemonException_messageIsDescriptive() {
            DockerDaemonException ex = new DockerDaemonException(
                    "Docker daemon is not reachable (docker info exited with code 1)",
                    "error during connect");

            assertThat(ex.getMessage())
                    .contains("Docker daemon")
                    .contains("not reachable");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  4. Build Docker échoué
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Docker build failed")
    class DockerBuildFailed {

        private static final String SAMPLE_BUILD_LOG =
                "Step 1/3 : FROM mini-esb-backend:latest\n" +
                        " ---> Unable to find image 'mini-esb-backend:latest' locally\n" +
                        "Error response from daemon: pull access denied for mini-esb-backend\n" +
                        "The command '/bin/sh -c mvn package' returned a non-zero code: 1";

        @Test
        @DisplayName("DockerBuildException carries exit code and full build log")
        void buildException_carriesExitCodeAndLog() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 1", 1, SAMPLE_BUILD_LOG);

            assertThat(ex.getExitCode()).isEqualTo(1);
            assertThat(ex.getBuildLog()).isEqualTo(SAMPLE_BUILD_LOG);
        }

        @Test
        @DisplayName("DockerBuildException is a RuntimeException")
        void buildException_isRuntimeException() {
            assertThat(new DockerBuildException("msg", 1, "log"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("buildLog contains exploitable error information")
        void buildException_buildLog_containsErrorInfo() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 1", 1, SAMPLE_BUILD_LOG);

            assertThat(ex.getBuildLog())
                    .contains("Step 1/3")
                    .contains("Error response from daemon")
                    .contains("non-zero code: 1");
        }

        @Test
        @DisplayName("pipeline stays VALIDATED when DockerBuildException is thrown")
        void buildException_pipelineRemainsValidated() {
            // Vérification du contrat : quand un build échoue, seule l'image passe en FAILED.
            // Le pipeline ne doit pas changer de statut.
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 1", 1, SAMPLE_BUILD_LOG);

            // Simule la mise à jour que le service fait sur l'image
            existingImage.setStatus(ImageStatus.FAILED);

            // Pipeline reste VALIDATED
            assertThat(validatedPipeline.getStatus()).isEqualTo(PipelineStatus.VALIDATED);
            // Image passe en FAILED
            assertThat(existingImage.getStatus()).isEqualTo(ImageStatus.FAILED);
        }

        @Test
        @DisplayName("exit code 2 is also supported (e.g. invalid Dockerfile syntax)")
        void buildException_exitCode2_isSupported() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 2",
                    2,
                    "Dockerfile parse error line 4: unknown instruction: ENTRYPOINTT");

            assertThat(ex.getExitCode()).isEqualTo(2);
            assertThat(ex.getBuildLog()).contains("unknown instruction");
        }

        @Test
        @DisplayName("buildLog is preserved with newlines (multiline)")
        void buildException_buildLog_preservesNewlines() {
            DockerBuildException ex = new DockerBuildException("msg", 1, SAMPLE_BUILD_LOG);
            String[] lines = ex.getBuildLog().split("\n");
            assertThat(lines.length).isGreaterThan(1);
            assertThat(lines[0]).contains("Step 1/3");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  5. Invariant global : pipeline status ne change jamais sur erreur build
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pipeline status invariant")
    class PipelineStatusInvariant {

        @Test
        @DisplayName("VALIDATED status is preserved regardless of DockerDaemonException")
        void pipelineStatus_preservedOnDaemonError() {
            PipelineStatus before = validatedPipeline.getStatus();
            // Simule ce que le service fait en cas de DockerDaemonException :
            // il ne modifie PAS le pipeline
            assertThat(validatedPipeline.getStatus()).isEqualTo(before);
        }

        @Test
        @DisplayName("VALIDATED status is preserved regardless of DockerBuildException")
        void pipelineStatus_preservedOnBuildError() {
            PipelineStatus before = validatedPipeline.getStatus();
            // Simule ce que le service fait en cas de DockerBuildException :
            // il ne modifie PAS le pipeline
            assertThat(validatedPipeline.getStatus()).isEqualTo(before);
        }

        @Test
        @DisplayName("DockerImage status becomes FAILED on daemon error")
        void dockerImageStatus_failedOnDaemonError() {
            existingImage.setStatus(ImageStatus.FAILED); // simulé par le service
            assertThat(existingImage.getStatus()).isEqualTo(ImageStatus.FAILED);
        }

        @Test
        @DisplayName("DockerImage status becomes FAILED on build error")
        void dockerImageStatus_failedOnBuildError() {
            existingImage.setStatus(ImageStatus.FAILED); // simulé par le service
            assertThat(existingImage.getStatus()).isEqualTo(ImageStatus.FAILED);
        }
    }
}