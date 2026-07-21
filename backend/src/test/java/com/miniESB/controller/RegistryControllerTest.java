package com.miniESB.controller;

import com.miniESB.dto.registry.CreateRegistryRequest;
import com.miniESB.dto.registry.RegistryResponse;
import com.miniESB.dto.registry.UpdateRegistryRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.RegistryService;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link RegistryController} — CRUD, pas de gestion
 * d'erreur locale (les exceptions remontent telles quelles, probablement
 * gérées par {@code GlobalExceptionHandler}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistryController")
class RegistryControllerTest {

    @Mock
    private RegistryService registryService;

    @InjectMocks
    private RegistryController controller;

    private static final Long REGISTRY_ID = 1L;

    // ══════════════════════════════════════════════════════════════════════════
    //  POST — create
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("returns 201 CREATED with the created registry")
        void create_success_returns201() {
            CreateRegistryRequest request =
                    new CreateRegistryRequest("Docker Hub", "https://index.docker.io", "aya", "pass");
            RegistryResponse expected = new RegistryResponse(1L, "Docker Hub", "https://index.docker.io", "aya");
            when(registryService.create(request)).thenReturn(expected);

            ResponseEntity<RegistryResponse> response = controller.create(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("propagates the exception when the service call fails")
        void create_serviceThrows_propagatesException() {
            CreateRegistryRequest request =
                    new CreateRegistryRequest("Bad", "https://x", "aya", "pass");
            when(registryService.create(request)).thenThrow(new RuntimeException("encryption failed"));

            assertThatThrownBy(() -> controller.create(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("encryption failed");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PATCH — update
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("returns 200 with the updated registry")
        void update_success_returns200() {
            UpdateRegistryRequest request = new UpdateRegistryRequest("New Name", null, null, null);
            RegistryResponse expected = new RegistryResponse(1L, "New Name", "https://index.docker.io", "aya");
            when(registryService.update(REGISTRY_ID, request)).thenReturn(expected);

            ResponseEntity<RegistryResponse> response = controller.update(REGISTRY_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when the registry does not exist")
        void update_unknownRegistry_propagatesException() {
            UpdateRegistryRequest request = new UpdateRegistryRequest("New Name", null, null, null);
            when(registryService.update(99L, request))
                    .thenThrow(new ResourceNotFoundException("Registry not found: 99"));

            assertThatThrownBy(() -> controller.update(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /{id} — getById
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns 200 with the registry")
        void getById_existingRegistry_returns200() {
            RegistryResponse expected = new RegistryResponse(1L, "Docker Hub", "https://index.docker.io", "aya");
            when(registryService.getById(REGISTRY_ID)).thenReturn(expected);

            ResponseEntity<RegistryResponse> response = controller.getById(REGISTRY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when the registry does not exist")
        void getById_unknownRegistry_propagatesException() {
            when(registryService.getById(99L))
                    .thenThrow(new ResourceNotFoundException("Registry not found: 99"));

            assertThatThrownBy(() -> controller.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET — getAll
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns 200 with all registries")
        void getAll_returnsAllRegistries() {
            List<RegistryResponse> expected = List.of(
                    new RegistryResponse(1L, "Docker Hub", "https://index.docker.io", "aya"),
                    new RegistryResponse(2L, "GHCR", "https://ghcr.io", "bob"));
            when(registryService.getAll()).thenReturn(expected);

            ResponseEntity<List<RegistryResponse>> response = controller.getAll();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns 200 with an empty list when no registries exist")
        void getAll_noRegistries_returnsEmptyList() {
            when(registryService.getAll()).thenReturn(List.of());

            ResponseEntity<List<RegistryResponse>> response = controller.getAll();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DELETE — delete
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("returns 204 NO_CONTENT and delegates to registryService.delete")
        void delete_success_returns204() {
            ResponseEntity<Void> response = controller.delete(REGISTRY_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
            verify(registryService).delete(REGISTRY_ID);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when the registry does not exist")
        void delete_unknownRegistry_propagatesException() {
            doThrow(new ResourceNotFoundException("Registry not found: 99"))
                    .when(registryService).delete(99L);

            assertThatThrownBy(() -> controller.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}