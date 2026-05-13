package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Provider;
import com.miniESB.dto.provider.CreateProviderRequest;
import com.miniESB.dto.provider.ProviderResponse;
import com.miniESB.dto.provider.UpdateProviderRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.service.impl.ProviderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderServiceImpl")
class ProviderServiceImplTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderServiceImpl providerService;

    private Provider provider;

    @BeforeEach
    void setUp() {
        provider = new Provider();
        provider.setId(1L);
        provider.setName("REST Provider");
        provider.setEndpoint("http://api.example.com");
        provider.setProtocol("HTTP");
        provider.setTimeout(5000);
    }

    // =========================================================================
    // createProvider()
    // =========================================================================

    @Nested
    @DisplayName("createProvider()")
    class CreateProvider {

        @Test
        @DisplayName("saves provider and returns response DTO")
        void createProvider_success() {
            CreateProviderRequest req = new CreateProviderRequest(
                    "REST Provider", "http://api.example.com", "HTTP", 5000);
            when(providerRepository.save(any(Provider.class))).thenReturn(provider);

            ProviderResponse result = providerService.createProvider(req);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("REST Provider");
            assertThat(result.endpoint()).isEqualTo("http://api.example.com");
            assertThat(result.protocol()).isEqualTo("HTTP");
            assertThat(result.timeout()).isEqualTo(5000);
        }
    }

    // =========================================================================
    // updateProvider()
    // =========================================================================

    @Nested
    @DisplayName("updateProvider()")
    class UpdateProvider {

        @Test
        @DisplayName("updates only non-null fields")
        void updateProvider_partialUpdate() {
            UpdateProviderRequest req = new UpdateProviderRequest("New Name", null, null, null);
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(providerRepository.save(any())).thenReturn(provider);

            ProviderResponse result = providerService.updateProvider(1L, req);

            assertThat(result).isNotNull();
            verify(providerRepository).save(provider);
            // name should have been updated in-place
            assertThat(provider.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("updates all fields when all are provided")
        void updateProvider_fullUpdate() {
            UpdateProviderRequest req = new UpdateProviderRequest(
                    "Updated", "http://new.com", "HTTPS", 3000);
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(providerRepository.save(any())).thenReturn(provider);

            providerService.updateProvider(1L, req);

            assertThat(provider.getName()).isEqualTo("Updated");
            assertThat(provider.getEndpoint()).isEqualTo("http://new.com");
            assertThat(provider.getProtocol()).isEqualTo("HTTPS");
            assertThat(provider.getTimeout()).isEqualTo(3000);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when provider not found")
        void updateProvider_notFound() {
            when(providerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.updateProvider(99L, new UpdateProviderRequest(null, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deleteProvider()
    // =========================================================================

    @Nested
    @DisplayName("deleteProvider()")
    class DeleteProvider {

        @Test
        @DisplayName("deletes provider when it exists")
        void deleteProvider_success() {
            when(providerRepository.existsById(1L)).thenReturn(true);

            providerService.deleteProvider(1L);

            verify(providerRepository).deleteById(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when provider not found")
        void deleteProvider_notFound() {
            when(providerRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> providerService.deleteProvider(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(providerRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // getProviderById()
    // =========================================================================

    @Nested
    @DisplayName("getProviderById()")
    class GetProviderById {

        @Test
        @DisplayName("returns DTO when provider exists")
        void getProviderById_found() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            ProviderResponse result = providerService.getProviderById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("REST Provider");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void getProviderById_notFound() {
            when(providerRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.getProviderById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getAllProviders()
    // =========================================================================

    @Nested
    @DisplayName("getAllProviders()")
    class GetAllProviders {

        @Test
        @DisplayName("returns all providers")
        void getAllProviders_returnsList() {
            when(providerRepository.findAll()).thenReturn(List.of(provider));

            List<ProviderResponse> result = providerService.getAllProviders();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("REST Provider");
        }

        @Test
        @DisplayName("returns empty list when no providers")
        void getAllProviders_empty() {
            when(providerRepository.findAll()).thenReturn(List.of());

            assertThat(providerService.getAllProviders()).isEmpty();
        }
    }
}