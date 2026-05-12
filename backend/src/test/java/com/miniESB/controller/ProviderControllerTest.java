package com.miniESB.controller;


import com.miniESB.dto.provider.CreateProviderRequest;
import com.miniESB.dto.provider.ProviderResponse;
import com.miniESB.dto.provider.UpdateProviderRequest;
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
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderController — unit tests")
class ProviderControllerTest {

    @Mock  private ProviderService providerService;
    @InjectMocks private ProviderController providerController;

    private final ProviderResponse providerDto =
            new ProviderResponse(1L, "REST Provider", "http://api.example.com", "HTTP", 5000);

    @Nested @DisplayName("createProvider()")
    class CreateProvider {

        @Test @DisplayName("returns 201 with created provider")
        void createProvider_returns201() {
            CreateProviderRequest req = new CreateProviderRequest(
                    "REST Provider", "http://api.example.com", "HTTP", 5000);
            when(providerService.createProvider(req)).thenReturn(providerDto);

            ResponseEntity<ProviderResponse> response = providerController.createProvider(req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().name()).isEqualTo("REST Provider");
        }
    }

    @Nested @DisplayName("updateProvider()")
    class UpdateProvider {

        @Test @DisplayName("returns 200 with updated provider")
        void updateProvider_returns200() {
            UpdateProviderRequest req = new UpdateProviderRequest("Updated", null, null, null);
            when(providerService.updateProvider(1L, req)).thenReturn(providerDto);

            ResponseEntity<ProviderResponse> response = providerController.updateProvider(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when provider not found")
        void updateProvider_notFound() {
            UpdateProviderRequest req = new UpdateProviderRequest(null, null, null, null);
            when(providerService.updateProvider(99L, req))
                    .thenThrow(new ResourceNotFoundException("Provider not found"));

            assertThatThrownBy(() -> providerController.updateProvider(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("deleteProvider()")
    class DeleteProvider {

        @Test @DisplayName("returns 204 on successful delete")
        void deleteProvider_returns204() {
            doNothing().when(providerService).deleteProvider(1L);

            ResponseEntity<Void> response = providerController.deleteProvider(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when provider not found")
        void deleteProvider_notFound() {
            doThrow(new ResourceNotFoundException("Provider not found"))
                    .when(providerService).deleteProvider(99L);

            assertThatThrownBy(() -> providerController.deleteProvider(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("getProviderById()")
    class GetProviderById {

        @Test @DisplayName("returns 200 with provider")
        void getProviderById_returns200() {
            when(providerService.getProviderById(1L)).thenReturn(providerDto);

            ResponseEntity<ProviderResponse> response = providerController.getProviderById(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(1L);
        }
    }

    @Nested @DisplayName("getAllProviders()")
    class GetAllProviders {

        @Test @DisplayName("returns 200 with all providers")
        void getAllProviders_returns200() {
            when(providerService.getAllProviders()).thenReturn(List.of(providerDto));

            ResponseEntity<List<ProviderResponse>> response = providerController.getAllProviders();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }
    }
}
