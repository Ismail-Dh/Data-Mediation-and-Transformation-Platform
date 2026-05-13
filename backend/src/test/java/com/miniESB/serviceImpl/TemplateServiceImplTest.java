package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.MappingTemplate;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.entity.ValidationTemplate;
import com.miniESB.domain.enums.TemplateStatus;
import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateRequest;
import com.miniESB.dto.template.TemplateResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingTemplateRepository;
import com.miniESB.repository.UserRepository;
import com.miniESB.repository.ValidationTemplateRepository;
import com.miniESB.service.impl.TemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateServiceImpl")
class TemplateServiceImplTest {

    @Mock private ValidationTemplateRepository validationRepo;
    @Mock private MappingTemplateRepository mappingRepo;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TemplateServiceImpl templateService;

    private User admin;
    private ValidationTemplate draftValidation;
    private ValidationTemplate publishedValidation;
    private MappingTemplate draftMapping;


    Map<String, Object> validationContent = Map.of(
            "rules", List.of(
                    Map.of(
                            "field", "orderId",
                            "ruleType", "NOT_NULL"
                    ),
                    Map.of(
                            "field", "email",
                            "ruleType", "REGEX_EMAIL"
                    ),
                    Map.of(
                            "field", "amount",
                            "ruleType", "TYPE_NUMBER"
                    ),
                    Map.of(
                            "field", "phone",
                            "ruleType", "REGEX_PHONE"
                    )
            )
    );

    Map<String, Object> mappingContent = Map.of(
            "mappings", List.of(
                    Map.of(
                            "type", "DIRECT",
                            "source", "firstName",
                            "target", "first_name"
                    ),
                    Map.of(
                            "type", "DIRECT",
                            "source", "lastName",
                            "target", "last_name"
                    ),
                    Map.of(
                            "type", "DATE_FORMAT",
                            "source", "birthDate",
                            "target", "dob",
                            "expression", "yyyy-MM-dd"
                    ),
                    Map.of(
                            "type", "EXPRESSION",
                            "source", "amount",
                            "target", "total_price",
                            "expression", "amount * 1.20"
                    )
            )
    );
    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");


        draftValidation = ValidationTemplate.builder()
                .id(10L).name("Draft Val").description("desc").content(validationContent)
                .status(TemplateStatus.DRAFT).version(1).parentId(null)
                .createdBy(admin).createdAt(LocalDateTime.now()).build();

        publishedValidation = ValidationTemplate.builder()
                .id(11L).name("Published Val").description("desc").content(validationContent)
                .status(TemplateStatus.PUBLISHED).version(1).parentId(null)
                .createdBy(admin).createdAt(LocalDateTime.now()).build();

        draftMapping = MappingTemplate.builder()
                .id(20L).name("Draft Map").description("desc").content(mappingContent)
                .status(TemplateStatus.DRAFT).version(1).parentId(null)
                .createdBy(admin).createdAt(LocalDateTime.now()).build();
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates a VALIDATION template in DRAFT state")
        void create_validationTemplate() {
            TemplateRequest req = new TemplateRequest("Draft Val", "desc", TemplateType.VALIDATION, validationContent);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(validationRepo.save(any())).thenReturn(draftValidation);

            TemplateResponse result = templateService.create(req, "admin");

            assertThat(result.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(result.type()).isEqualTo(TemplateType.VALIDATION);
            verify(validationRepo).save(any(ValidationTemplate.class));
        }

        @Test
        @DisplayName("creates a MAPPING template in DRAFT state")
        void create_mappingTemplate() {
            TemplateRequest req = new TemplateRequest("Draft Map", "desc", TemplateType.MAPPING, mappingContent);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(mappingRepo.save(any())).thenReturn(draftMapping);

            TemplateResponse result = templateService.create(req, "admin");

            assertThat(result.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(result.type()).isEqualTo(TemplateType.MAPPING);
            verify(mappingRepo).save(any(MappingTemplate.class));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when admin user not found")
        void create_adminNotFound() {
            TemplateRequest req = new TemplateRequest("T", "d", TemplateType.VALIDATION, validationContent);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> templateService.create(req, "ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("updates DRAFT validation template in place")
        void update_draftUpdatedInPlace() {
            TemplateRequest req = new TemplateRequest("New Name", "new desc", TemplateType.VALIDATION, validationContent);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(validationRepo.findById(10L)).thenReturn(Optional.of(draftValidation));
            when(validationRepo.save(any())).thenReturn(draftValidation);

            TemplateResponse result = templateService.update(10L, TemplateType.VALIDATION, req, "admin");

            assertThat(result).isNotNull();
            // ensure save was called on the same object (not a new one)
            verify(validationRepo).save(draftValidation);
        }

        @Test
        @DisplayName("PUBLISHED validation template forks a new DRAFT version")
        void update_publishedForksNewVersion() {
            TemplateRequest req = new TemplateRequest("V2", "desc", TemplateType.VALIDATION, validationContent);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(validationRepo.findById(11L)).thenReturn(Optional.of(publishedValidation));

            ValidationTemplate newVersion = ValidationTemplate.builder()
                    .id(12L).name("V2").version(2).status(TemplateStatus.DRAFT)
                    .parentId(11L).createdBy(admin).createdAt(LocalDateTime.now()).build();
            when(validationRepo.save(any())).thenReturn(newVersion);

            TemplateResponse result = templateService.update(11L, TemplateType.VALIDATION, req, "admin");

            ArgumentCaptor<ValidationTemplate> captor = ArgumentCaptor.forClass(ValidationTemplate.class);
            verify(validationRepo).save(captor.capture());

            ValidationTemplate saved = captor.getValue();
            assertThat(saved.getVersion()).isEqualTo(2);
            assertThat(saved.getParentId()).isEqualTo(11L);
            assertThat(saved.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when updating DISABLED template")
        void update_disabledThrows() {
            ValidationTemplate disabled = ValidationTemplate.builder()
                    .id(13L).status(TemplateStatus.DISABLED).version(1)
                    .createdBy(admin).build();

            TemplateRequest req = new TemplateRequest("X", "x", TemplateType.VALIDATION, validationContent);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(validationRepo.findById(13L)).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> templateService.update(13L, TemplateType.VALIDATION, req, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DISABLED");
        }
    }

    // =========================================================================
    // publish()
    // =========================================================================

    @Nested
    @DisplayName("publish()")
    class Publish {

        @Test
        @DisplayName("transitions DRAFT → PUBLISHED for validation template")
        void publish_draftToPublished() {
            when(validationRepo.findById(10L)).thenReturn(Optional.of(draftValidation));
            when(validationRepo.save(any())).thenReturn(draftValidation);

            TemplateResponse result = templateService.publish(10L, TemplateType.VALIDATION);

            assertThat(draftValidation.getStatus()).isEqualTo(TemplateStatus.PUBLISHED);
            verify(validationRepo).save(draftValidation);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for invalid transition PUBLISHED → PUBLISHED")
        void publish_invalidTransition() {
            when(validationRepo.findById(11L)).thenReturn(Optional.of(publishedValidation));

            assertThatThrownBy(() -> templateService.publish(11L, TemplateType.VALIDATION))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("transitions DRAFT → PUBLISHED for mapping template")
        void publish_mappingDraftToPublished() {
            when(mappingRepo.findById(20L)).thenReturn(Optional.of(draftMapping));
            when(mappingRepo.save(any())).thenReturn(draftMapping);

            templateService.publish(20L, TemplateType.MAPPING);

            assertThat(draftMapping.getStatus()).isEqualTo(TemplateStatus.PUBLISHED);
        }
    }

    // =========================================================================
    // disable()
    // =========================================================================

    @Nested
    @DisplayName("disable()")
    class Disable {

        @Test
        @DisplayName("transitions PUBLISHED → DISABLED for validation template")
        void disable_publishedToDisabled() {
            when(validationRepo.findById(11L)).thenReturn(Optional.of(publishedValidation));
            when(validationRepo.save(any())).thenReturn(publishedValidation);

            templateService.disable(11L, TemplateType.VALIDATION);

            assertThat(publishedValidation.getStatus()).isEqualTo(TemplateStatus.DISABLED);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for invalid transition DRAFT → DISABLED")
        void disable_draftThrows() {
            when(validationRepo.findById(10L)).thenReturn(Optional.of(draftValidation));

            assertThatThrownBy(() -> templateService.disable(10L, TemplateType.VALIDATION))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // findAll()
    // =========================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns all validation templates regardless of status")
        void findAll_validation() {
            when(validationRepo.findAll()).thenReturn(List.of(draftValidation, publishedValidation));

            List<TemplateResponse> result = templateService.findAll(TemplateType.VALIDATION);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns all mapping templates")
        void findAll_mapping() {
            when(mappingRepo.findAll()).thenReturn(List.of(draftMapping));

            List<TemplateResponse> result = templateService.findAll(TemplateType.MAPPING);

            assertThat(result).hasSize(1);
        }
    }

    // =========================================================================
    // findPublished()
    // =========================================================================

    @Nested
    @DisplayName("findPublished()")
    class FindPublished {

        @Test
        @DisplayName("returns only PUBLISHED validation templates")
        void findPublished_validation() {
            when(validationRepo.findAllByStatus(TemplateStatus.PUBLISHED))
                    .thenReturn(List.of(publishedValidation));

            List<TemplateResponse> result = templateService.findPublished(TemplateType.VALIDATION);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(TemplateStatus.PUBLISHED);
        }

        @Test
        @DisplayName("returns empty list when no published mapping templates")
        void findPublished_mappingEmpty() {
            when(mappingRepo.findAllByStatus(TemplateStatus.PUBLISHED)).thenReturn(List.of());

            assertThat(templateService.findPublished(TemplateType.MAPPING)).isEmpty();
        }
    }
}