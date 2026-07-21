package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.domain.entity.Registry;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.dto.docker.PushImageResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.DockerImageRepository;
import com.miniESB.repository.RegistryRepository;
import com.miniESB.service.RegistryService;
import com.miniESB.service.docker.CommandExecutor;
import com.miniESB.service.docker.CommandResult;
import com.miniESB.service.impl.DockerImagePushService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DockerImagePushService}, après refactoring vers
 * {@link CommandExecutor} (plus de {@code ProcessBuilder} direct).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerImagePushService")
class DockerImagePushServiceTest {

    @Mock private DockerImageRepository dockerImageRepository;
    @Mock private RegistryRepository registryRepository;
    @Mock private RegistryService registryService;
    @Mock private CommandExecutor commandExecutor;

    @InjectMocks
    private DockerImagePushService service;

    private DockerImage successImage;
    private Registry registry;

    @BeforeEach
    void setUp() {
        successImage = DockerImage.builder()
                .id(10L)
                .status(ImageStatus.SUCCESS)
                .imageName("pipeline-1")
                .tag("1.0.0")
                .build();

        registry = Registry.builder()
                .id(5L)
                .name("My Private Registry")
                .url("registry.example.com")
                .username("aya")
                .encryptedPassword("ENC(secret)")
                .build();
    }

    /** Stub par défaut : toutes les commandes docker réussissent (exit 0). */
    private void stubAllCommandsSucceed() throws Exception {
        when(commandExecutor.runWithStdin(anyList(), anyString()))
                .thenReturn(new CommandResult(0, "Login Succeeded"));
        when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("tag"))))
                .thenReturn(new CommandResult(0, ""));
        when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("push")), any()))
                .thenReturn(new CommandResult(0, "pushed"));
        when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                .thenReturn(new CommandResult(0, ""));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Garde-fous
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Guard clauses")
    class GuardClauses {

        @Test
        @DisplayName("throws ResourceNotFoundException when no image exists for the pipeline")
        void pushImage_noImage_throwsResourceNotFound() {
            when(dockerImageRepository.findByPipelineId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pushImage(99L, 5L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(registryRepository, commandExecutor);
        }

        @Test
        @DisplayName("throws IllegalStateException when image status is not SUCCESS")
        void pushImage_imageNotSuccess_throwsIllegalState() {
            DockerImage buildingImage = DockerImage.builder()
                    .id(10L).status(ImageStatus.BUILDING).build();
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(buildingImage));

            assertThatThrownBy(() -> service.pushImage(1L, 5L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUILDING");

            verifyNoInteractions(registryRepository, commandExecutor);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when registry does not exist")
        void pushImage_unknownRegistry_throwsResourceNotFound() {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pushImage(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(commandExecutor);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns SUCCESS response and links the registry to the image")
        void pushImage_allStepsSucceed_returnsSuccessAndLinksRegistry() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("plainPassword");
            stubAllCommandsSucceed();

            PushImageResponse response = service.pushImage(1L, 5L);

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.registryName()).isEqualTo("My Private Registry");
            assertThat(response.imageFullName()).isEqualTo("registry.example.com/aya/pipeline-1:1.0.0");

            assertThat(successImage.getRegistry()).isEqualTo(registry);
            verify(dockerImageRepository).save(successImage);
        }

        @Test
        @DisplayName("builds a Docker Hub style tag (no URL prefix) when registry URL is blank")
        void pushImage_dockerHubRegistry_buildsTagWithoutUrl() throws Exception {
            Registry dockerHub = Registry.builder()
                    .id(6L).name("Docker Hub").url(null).username("aya")
                    .encryptedPassword("ENC(x)").build();
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(6L)).thenReturn(Optional.of(dockerHub));
            when(registryService.decryptPassword(6L)).thenReturn("plainPassword");
            stubAllCommandsSucceed();

            PushImageResponse response = service.pushImage(1L, 6L);

            assertThat(response.imageFullName()).isEqualTo("aya/pipeline-1:1.0.0");
        }

        @Test
        @DisplayName("sends the password via stdin to 'docker login', never as a plain argument")
        void pushImage_login_sendsPasswordViaStdinOnly() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("s3cr3t");
            stubAllCommandsSucceed();

            service.pushImage(1L, 5L);

            ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<String> stdinCaptor = ArgumentCaptor.forClass(String.class);
            verify(commandExecutor).runWithStdin(cmdCaptor.capture(), stdinCaptor.capture());

            assertThat(cmdCaptor.getValue()).contains("--password-stdin")
                    .doesNotContain("s3cr3t");
            assertThat(stdinCaptor.getValue()).isEqualTo("s3cr3t");
        }

        @Test
        @DisplayName("always calls docker logout, even after a fully successful push")
        void pushImage_success_stillCallsLogout() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            stubAllCommandsSucceed();

            service.pushImage(1L, 5L);

            verify(commandExecutor).run(argThat(cmd -> cmd.contains("logout")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Échecs en cours de push
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failures during push")
    class Failures {

        @Test
        @DisplayName("throws when docker login fails, and never attempts tag or push")
        void pushImage_loginFails_throwsAndSkipsTagAndPush() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            when(commandExecutor.runWithStdin(anyList(), anyString()))
                    .thenReturn(new CommandResult(1, "unauthorized"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                    .thenReturn(new CommandResult(0, ""));

            assertThatThrownBy(() -> service.pushImage(1L, 5L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("docker login failed");

            verify(commandExecutor, never()).run(argThat(cmd -> cmd != null && cmd.contains("tag")));
            verify(commandExecutor, never()).run(argThat(cmd -> cmd != null && cmd.contains("push")), any());
            verify(dockerImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when docker tag fails, and never attempts push")
        void pushImage_tagFails_throwsAndSkipsPush() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            when(commandExecutor.runWithStdin(anyList(), anyString()))
                    .thenReturn(new CommandResult(0, "Login Succeeded"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("tag"))))
                    .thenReturn(new CommandResult(1, ""));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                    .thenReturn(new CommandResult(0, ""));

            assertThatThrownBy(() -> service.pushImage(1L, 5L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("docker tag failed");

            verify(commandExecutor, never()).run(argThat(cmd -> cmd != null && cmd.contains("push")), any());
            verify(dockerImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when docker push fails, and does not link registry to image")
        void pushImage_pushFails_throwsAndDoesNotLinkRegistry() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            when(commandExecutor.runWithStdin(anyList(), anyString()))
                    .thenReturn(new CommandResult(0, "Login Succeeded"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("tag"))))
                    .thenReturn(new CommandResult(0, ""));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("push")), any()))
                    .thenReturn(new CommandResult(1, "denied: requested access to the resource is denied"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                    .thenReturn(new CommandResult(0, ""));

            assertThatThrownBy(() -> service.pushImage(1L, 5L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("docker push failed");

            assertThat(successImage.getRegistry()).isNull();
            verify(dockerImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("still calls docker logout even when login/tag/push fails (finally block)")
        void pushImage_pushFails_stillCallsLogout() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            when(commandExecutor.runWithStdin(anyList(), anyString()))
                    .thenReturn(new CommandResult(1, "unauthorized"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                    .thenReturn(new CommandResult(0, ""));

            assertThatThrownBy(() -> service.pushImage(1L, 5L)).isInstanceOf(RuntimeException.class);

            verify(commandExecutor).run(argThat(cmd -> cmd.contains("logout")));
        }

        @Test
        @DisplayName("propagates the original push failure even if logout itself also fails")
        void pushImage_logoutAlsoFails_originalExceptionStillPropagates() throws Exception {
            when(dockerImageRepository.findByPipelineId(1L)).thenReturn(Optional.of(successImage));
            when(registryRepository.findById(5L)).thenReturn(Optional.of(registry));
            when(registryService.decryptPassword(5L)).thenReturn("pw");
            when(commandExecutor.runWithStdin(anyList(), anyString()))
                    .thenReturn(new CommandResult(1, "unauthorized"));
            when(commandExecutor.run(argThat(cmd -> cmd != null && cmd.contains("logout"))))
                    .thenThrow(new RuntimeException("logout also broken"));

            assertThatThrownBy(() -> service.pushImage(1L, 5L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("docker login failed");
        }
    }
}