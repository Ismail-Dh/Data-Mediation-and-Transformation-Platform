package com.miniESB.serviceImpl;


import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.Provider;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.domain.enums.UserRole;
import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.repository.UserRepository;
import com.miniESB.service.impl.PipelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineServiceImpl")
class PipelineServiceImplTest {

    @Mock private PipelineRepository pipelineRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProviderRepository providerRepository;

    @InjectMocks
    private PipelineServiceImpl pipelineService;

    private User developer;
    private User admin;
    private Provider provider;
    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        developer = new User();
        developer.setId(1L);
        developer.setUsername("dev1");
        developer.setRole(UserRole.DEVELOPER);

        admin = new User();
        admin.setId(2L);
        admin.setUsername("admin");
        admin.setRole(UserRole.ADMIN);

        provider = new Provider();
        provider.setId(10L);
        provider.setName("REST Provider");
        provider.setEndpoint("http://api.example.com");

        pipeline = Pipeline.builder()
                .id(100L)
                .name("My Pipeline")
                .version("1.0")
                .inputFormat(DataFormat.JSON)
                .outputFormat(DataFormat.XML)
                .status(PipelineStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .createdBy(developer)
                .provider(provider)
                .build();
    }

    // =========================================================================
    // createPipeline()
    // =========================================================================

    @Nested
    @DisplayName("createPipeline()")
    class CreatePipeline {

        @Test
        @DisplayName("creates pipeline with provider and returns response")
        void createPipeline_withProvider() {
            CreatePipelineRequest req = new CreatePipelineRequest(
                    "My Pipeline", "1.0", "JSON", "XML", 10L);

            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));
            when(providerRepository.findById(10L)).thenReturn(Optional.of(provider));
            when(pipelineRepository.save(any())).thenReturn(pipeline);

            PipelineResponse result = pipelineService.createPipeline(req, "dev1");

            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.name()).isEqualTo("My Pipeline");
            assertThat(result.createdBy()).isEqualTo("dev1");
            assertThat(result.providerId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("creates pipeline without provider when providerId is null")
        void createPipeline_withoutProvider() {
            Pipeline pipelineNoProvider = Pipeline.builder()
                    .id(101L).name("No Provider").version("1.0")
                    .inputFormat(DataFormat.JSON).outputFormat(DataFormat.XML)
                    .status(PipelineStatus.DRAFT).createdAt(LocalDateTime.now())
                    .createdBy(developer).provider(null).build();

            CreatePipelineRequest req = new CreatePipelineRequest(
                    "No Provider", "1.0", "JSON", "XML", null);

            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));
            when(pipelineRepository.save(any())).thenReturn(pipelineNoProvider);

            PipelineResponse result = pipelineService.createPipeline(req, "dev1");

            assertThat(result.providerId()).isNull();
            verify(providerRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void createPipeline_userNotFound() {
            CreatePipelineRequest req = new CreatePipelineRequest(
                    "P", "1.0", "JSON", "XML", null);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineService.createPipeline(req, "ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when provider not found")
        void createPipeline_providerNotFound() {
            CreatePipelineRequest req = new CreatePipelineRequest(
                    "P", "1.0", "JSON", "XML", 99L);
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));
            when(providerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineService.createPipeline(req, "dev1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // updatePipeline()
    // =========================================================================

    @Nested
    @DisplayName("updatePipeline()")
    class UpdatePipeline {

        @Test
        @DisplayName("owner can update own pipeline")
        void updatePipeline_ownerSuccess() {
            UpdatePipelineRequest req = new UpdatePipelineRequest("Updated", null, null, null, null);
            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));
            when(pipelineRepository.save(any())).thenReturn(pipeline);

            PipelineResponse result = pipelineService.updatePipeline(100L, req, "dev1");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("admin can update any pipeline")
        void updatePipeline_adminSuccess() {
            UpdatePipelineRequest req = new UpdatePipelineRequest("Admin Edit", null, null, null, null);
            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(pipelineRepository.save(any())).thenReturn(pipeline);

            assertThatCode(() -> pipelineService.updatePipeline(100L, req, "admin"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("non-owner developer gets AccessDeniedException")
        void updatePipeline_notOwner() {
            User otherDev = new User();
            otherDev.setUsername("dev2");
            otherDev.setRole(UserRole.DEVELOPER);

            UpdatePipelineRequest req = new UpdatePipelineRequest("Hack", null, null, null, null);
            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev2")).thenReturn(Optional.of(otherDev));

            assertThatThrownBy(() -> pipelineService.updatePipeline(100L, req, "dev2"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // =========================================================================
    // deletePipeline()
    // =========================================================================

    @Nested
    @DisplayName("deletePipeline()")
    class DeletePipeline {

        @Test
        @DisplayName("owner can delete own pipeline")
        void deletePipeline_ownerSuccess() {
            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));

            pipelineService.deletePipeline(100L, "dev1");

            verify(pipelineRepository).delete(pipeline);
        }

        @Test
        @DisplayName("non-owner developer gets AccessDeniedException")
        void deletePipeline_notOwner() {
            User otherDev = new User();
            otherDev.setUsername("dev2");
            otherDev.setRole(UserRole.DEVELOPER);

            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev2")).thenReturn(Optional.of(otherDev));

            assertThatThrownBy(() -> pipelineService.deletePipeline(100L, "dev2"))
                    .isInstanceOf(AccessDeniedException.class);

            verify(pipelineRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline not found")
        void deletePipeline_notFound() {
            when(pipelineRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineService.deletePipeline(999L, "dev1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getPipelineById()
    // =========================================================================

    @Nested
    @DisplayName("getPipelineById()")
    class GetPipelineById {

        @Test
        @DisplayName("owner gets own pipeline")
        void getPipelineById_ownerSuccess() {
            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));

            PipelineResponse result = pipelineService.getPipelineById(100L, "dev1");

            assertThat(result.id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("non-owner developer gets AccessDeniedException")
        void getPipelineById_notOwner() {
            User otherDev = new User();
            otherDev.setUsername("dev2");
            otherDev.setRole(UserRole.DEVELOPER);

            when(pipelineRepository.findById(100L)).thenReturn(Optional.of(pipeline));
            when(userRepository.findByUsername("dev2")).thenReturn(Optional.of(otherDev));

            assertThatThrownBy(() -> pipelineService.getPipelineById(100L, "dev2"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // =========================================================================
    // getMyPipelines()
    // =========================================================================

    @Nested
    @DisplayName("getMyPipelines()")
    class GetMyPipelines {

        @Test
        @DisplayName("returns pipelines owned by the user")
        void getMyPipelines_returnsOwnedList() {
            when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer));
            when(pipelineRepository.findByCreatedBy(developer)).thenReturn(List.of(pipeline));

            List<PipelineResponse> result = pipelineService.getMyPipelines("dev1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).createdBy()).isEqualTo("dev1");
        }
    }

    // =========================================================================
    // getAllPipelines()
    // =========================================================================

    @Nested
    @DisplayName("getAllPipelines()")
    class GetAllPipelines {

        @Test
        @DisplayName("returns all pipelines")
        void getAllPipelines_returnsList() {
            when(pipelineRepository.findAll()).thenReturn(List.of(pipeline));

            List<PipelineResponse> result = pipelineService.getAllPipelines();

            assertThat(result).hasSize(1);
        }
    }
}