package com.miniESB.service;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.dto.docker.VersionBumpRequest;
import com.miniESB.dto.docker.VersionBumpResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.DockerImageRepository;
import com.miniESB.repository.PipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link ImageVersionService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImageVersionService")
class ImageVersionServiceTest {

    @Mock private DockerImageRepository dockerImageRepository;
    @Mock private PipelineRepository pipelineRepository;

    @InjectMocks
    private ImageVersionService service;

    private Pipeline pipeline;
    private DockerImage dockerImage;

    @BeforeEach
    void setUp() {
        pipeline = Pipeline.builder()
                .id(1L)
                .version("1.2")
                .build();

        dockerImage = DockerImage.builder()
                .id(10L)
                .versionPatch(0)
                .lastPipelineVersion(null)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  computeNextTag()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("computeNextTag")
    class ComputeNextTag {

        @Test
        @DisplayName("returns patch 0 when it's the first build (lastPipelineVersion is null)")
        void firstBuild_returnsPatchZero() {
            dockerImage.setLastPipelineVersion(null);

            String tag = service.computeNextTag(dockerImage, pipeline);

            assertThat(tag).isEqualTo("1.2.0");
        }

        @Test
        @DisplayName("resets patch to 0 when pipeline.version changed since last build")
        void pipelineVersionChanged_resetsPatchToZero() {
            dockerImage.setLastPipelineVersion("1.1");
            dockerImage.setVersionPatch(5);
            pipeline.setVersion("1.2");

            String tag = service.computeNextTag(dockerImage, pipeline);

            assertThat(tag).isEqualTo("1.2.0");
        }

        @Test
        @DisplayName("increments patch when pipeline.version is unchanged")
        void pipelineVersionUnchanged_incrementsPatch() {
            dockerImage.setLastPipelineVersion("1.2");
            dockerImage.setVersionPatch(3);
            pipeline.setVersion("1.2");

            String tag = service.computeNextTag(dockerImage, pipeline);

            assertThat(tag).isEqualTo("1.2.4");
        }

        @Test
        @DisplayName("defaults pipeline version to 1.0 when pipeline.version is null")
        void pipelineVersionNull_defaultsToOneDotZero() {
            pipeline.setVersion(null);
            dockerImage.setLastPipelineVersion(null);

            String tag = service.computeNextTag(dockerImage, pipeline);

            assertThat(tag).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("does not persist anything — repositories are never touched")
        void doesNotPersist() {
            service.computeNextTag(dockerImage, pipeline);

            verifyNoInteractions(dockerImageRepository, pipelineRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  applyNextVersion()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applyNextVersion")
    class ApplyNextVersion {

        @Test
        @DisplayName("updates versionPatch, lastPipelineVersion and tag on the entity")
        void updatesEntityFields() {
            service.applyNextVersion(dockerImage, "1.2.4", "1.2");

            assertThat(dockerImage.getVersionPatch()).isEqualTo(4);
            assertThat(dockerImage.getLastPipelineVersion()).isEqualTo("1.2");
            assertThat(dockerImage.getTag()).isEqualTo("1.2.4");
        }

        @Test
        @DisplayName("correctly extracts a multi-digit patch from the tag")
        void extractsMultiDigitPatch() {
            service.applyNextVersion(dockerImage, "3.0.12", "3.0");

            assertThat(dockerImage.getVersionPatch()).isEqualTo(12);
        }

        @Test
        @DisplayName("does not persist — caller is responsible for saving")
        void doesNotPersist() {
            service.applyNextVersion(dockerImage, "1.2.0", "1.2");

            verifyNoInteractions(dockerImageRepository, pipelineRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  bumpVersion()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bumpVersion")
    class BumpVersion {

        @Test
        @DisplayName("MINOR bump increments the minor number and keeps major")
        void minorBump_incrementsMinor() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, null);
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.previousVersion()).isEqualTo("1.2");
            assertThat(response.newVersion()).isEqualTo("1.3");
            assertThat(pipeline.getVersion()).isEqualTo("1.3");
        }

        @Test
        @DisplayName("MAJOR bump increments major and resets minor to 0")
        void majorBump_incrementsMajorResetsMinor() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MAJOR, null);
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.newVersion()).isEqualTo("2.0");
            assertThat(pipeline.getVersion()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("uses targetVersion directly when provided, ignoring bump type")
        void targetVersionProvided_usedDirectly() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, "5.0");
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.newVersion()).isEqualTo("5.0");
            assertThat(pipeline.getVersion()).isEqualTo("5.0");
        }

        @Test
        @DisplayName("blank targetVersion falls back to computed bump")
        void blankTargetVersion_fallsBackToComputedBump() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, "   ");
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.newVersion()).isEqualTo("1.3");
        }

        @Test
        @DisplayName("defaults current version to 1.0 when pipeline.version is null, then bumps")
        void nullCurrentVersion_defaultsThenBumps() {
            pipeline.setVersion(null);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, null);
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.previousVersion()).isEqualTo("1.0");
            assertThat(response.newVersion()).isEqualTo("1.1");
        }

        @Test
        @DisplayName("resets to 1.0 base when current version has an invalid format, then bumps")
        void invalidVersionFormat_resetsToOneDotZeroBeforeBump() {
            pipeline.setVersion("not-a-version");
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MAJOR, null);
            VersionBumpResponse response = service.bumpVersion(1L, request);

            assertThat(response.newVersion()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void unknownPipeline_throwsResourceNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, null);

            assertThatThrownBy(() -> service.bumpVersion(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(pipelineRepository, never()).save(any());
        }

        @Test
        @DisplayName("persists the updated pipeline version")
        void persistsUpdatedPipeline() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineRepository.save(any(Pipeline.class))).thenAnswer(inv -> inv.getArgument(0));

            VersionBumpRequest request = new VersionBumpRequest(VersionBumpRequest.BumpType.MINOR, null);
            service.bumpVersion(1L, request);

            verify(pipelineRepository).save(argThat(p -> "1.3".equals(p.getVersion())));
        }
    }
}