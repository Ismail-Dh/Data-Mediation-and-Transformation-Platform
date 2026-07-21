package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Registry;
import com.miniESB.dto.registry.CreateRegistryRequest;
import com.miniESB.dto.registry.RegistryResponse;
import com.miniESB.dto.registry.UpdateRegistryRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.RegistryRepository;
import com.miniESB.service.AesEncryptionService;
import com.miniESB.service.impl.RegistryServiceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link RegistryServiceImpl}.
 *
 * <p><strong>Hypothèse sur les DTOs</strong> : {@code CreateRegistryRequest} et
 * {@code UpdateRegistryRequest} sont supposés être des records
 * {@code (name, url, username, password)} d'après l'ordre d'utilisation dans
 * le service. À corriger si l'ordre réel diffère.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistryServiceImpl")
class RegistryServiceImplTest {

    @Mock private RegistryRepository registryRepository;
    @Mock private AesEncryptionService aesEncryptionService;

    @InjectMocks
    private RegistryServiceImpl service;

    private Registry existingRegistry;

    @BeforeEach
    void setUp() {
        existingRegistry = Registry.builder()
                .id(1L)
                .name("Docker Hub")
                .url("https://index.docker.io")
                .username("aya")
                .encryptedPassword("ENC(secret)")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  create()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("encrypts the password before saving and never returns it in the response")
        void create_encryptsPasswordAndReturnsResponseWithoutIt() {
            CreateRegistryRequest request =
                    new CreateRegistryRequest("Docker Hub", "https://index.docker.io", "aya", "plainPass");
            when(aesEncryptionService.encrypt("plainPass")).thenReturn("ENC(plainPass)");
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> {
                Registry r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            RegistryResponse response = service.create(request);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Docker Hub");
            assertThat(response.username()).isEqualTo("aya");

            verify(registryRepository).save(argThat(r ->
                    "ENC(plainPass)".equals(r.getEncryptedPassword())));
            verify(aesEncryptionService).encrypt("plainPass");
        }

        @Test
        @DisplayName("never stores the plaintext password on the entity")
        void create_neverStoresPlaintextPassword() {
            CreateRegistryRequest request =
                    new CreateRegistryRequest("GHCR", "https://ghcr.io", "aya", "s3cr3t");
            when(aesEncryptionService.encrypt("s3cr3t")).thenReturn("ENC(s3cr3t)");
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.create(request);

            verify(registryRepository).save(argThat(r ->
                    !"s3cr3t".equals(r.getEncryptedPassword())));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  update() — mise à jour partielle
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update — partial update semantics")
    class Update {

        @Test
        @DisplayName("updates only the name when other fields are null")
        void update_onlyNameProvided_updatesNameOnly() {
            UpdateRegistryRequest request = new UpdateRegistryRequest("New Name", null, null, null);
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> inv.getArgument(0));

            RegistryResponse response = service.update(1L, request);

            assertThat(response.name()).isEqualTo("New Name");
            assertThat(existingRegistry.getUrl()).isEqualTo("https://index.docker.io");
            assertThat(existingRegistry.getUsername()).isEqualTo("aya");
            assertThat(existingRegistry.getEncryptedPassword()).isEqualTo("ENC(secret)");
            verifyNoInteractions(aesEncryptionService);
        }

        @Test
        @DisplayName("re-encrypts and replaces the password only when a new password is provided")
        void update_passwordProvided_reencryptsPassword() {
            UpdateRegistryRequest request = new UpdateRegistryRequest(null, null, null, "newPlainPass");
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));
            when(aesEncryptionService.encrypt("newPlainPass")).thenReturn("ENC(newPlainPass)");
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> inv.getArgument(0));

            RegistryResponse response = service.update(1L, request);

            assertThat(existingRegistry.getEncryptedPassword()).isEqualTo("ENC(newPlainPass)");
            // Les autres champs restent inchangés
            assertThat(response.name()).isEqualTo("Docker Hub");
        }

        @Test
        @DisplayName("updates all fields when all are provided")
        void update_allFieldsProvided_updatesEverything() {
            UpdateRegistryRequest request =
                    new UpdateRegistryRequest("Renamed", "https://new-url.io", "newUser", "newPass");
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));
            when(aesEncryptionService.encrypt("newPass")).thenReturn("ENC(newPass)");
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.update(1L, request);

            assertThat(existingRegistry.getName()).isEqualTo("Renamed");
            assertThat(existingRegistry.getUrl()).isEqualTo("https://new-url.io");
            assertThat(existingRegistry.getUsername()).isEqualTo("newUser");
            assertThat(existingRegistry.getEncryptedPassword()).isEqualTo("ENC(newPass)");
        }

        @Test
        @DisplayName("does nothing and does not call encrypt when all fields are null")
        void update_allFieldsNull_noChangesNoEncryption() {
            UpdateRegistryRequest request = new UpdateRegistryRequest(null, null, null, null);
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));
            when(registryRepository.save(any(Registry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.update(1L, request);

            assertThat(existingRegistry.getName()).isEqualTo("Docker Hub");
            assertThat(existingRegistry.getEncryptedPassword()).isEqualTo("ENC(secret)");
            verifyNoInteractions(aesEncryptionService);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when registry does not exist")
        void update_unknownRegistry_throwsResourceNotFound() {
            when(registryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, new UpdateRegistryRequest("x", null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(registryRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getById() / getAll()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getById / getAll")
    class Read {

        @Test
        @DisplayName("getById returns the mapped response without the password")
        void getById_existingRegistry_returnsResponse() {
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));

            RegistryResponse response = service.getById(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Docker Hub");
            assertThat(response.url()).isEqualTo("https://index.docker.io");
            assertThat(response.username()).isEqualTo("aya");
        }

        @Test
        @DisplayName("getById throws ResourceNotFoundException when registry does not exist")
        void getById_unknownRegistry_throwsResourceNotFound() {
            when(registryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("getAll maps every registry to a response, preserving order")
        void getAll_returnsAllMappedRegistries() {
            Registry second = Registry.builder()
                    .id(2L).name("GHCR").url("https://ghcr.io")
                    .username("bob").encryptedPassword("ENC(x)").build();
            when(registryRepository.findAll()).thenReturn(List.of(existingRegistry, second));

            List<RegistryResponse> responses = service.getAll();

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).name()).isEqualTo("Docker Hub");
            assertThat(responses.get(1).name()).isEqualTo("GHCR");
        }

        @Test
        @DisplayName("getAll returns an empty list when no registries exist")
        void getAll_noRegistries_returnsEmptyList() {
            when(registryRepository.findAll()).thenReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  delete()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes the registry when it exists")
        void delete_existingRegistry_deletesIt() {
            when(registryRepository.existsById(1L)).thenReturn(true);

            service.delete(1L);

            verify(registryRepository).deleteById(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException and never calls deleteById when registry does not exist")
        void delete_unknownRegistry_throwsResourceNotFoundAndDoesNotDelete() {
            when(registryRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(registryRepository, never()).deleteById(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  decryptPassword()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("decryptPassword")
    class DecryptPassword {

        @Test
        @DisplayName("decrypts and returns the stored encrypted password")
        void decryptPassword_existingRegistry_returnsDecryptedValue() {
            when(registryRepository.findById(1L)).thenReturn(Optional.of(existingRegistry));
            when(aesEncryptionService.decrypt("ENC(secret)")).thenReturn("secret");

            String result = service.decryptPassword(1L);

            assertThat(result).isEqualTo("secret");
            verify(aesEncryptionService).decrypt("ENC(secret)");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when registry does not exist")
        void decryptPassword_unknownRegistry_throwsResourceNotFound() {
            when(registryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.decryptPassword(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(aesEncryptionService);
        }
    }
}