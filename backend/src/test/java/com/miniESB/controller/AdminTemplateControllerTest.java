package com.miniESB.controller;

import com.miniESB.domain.enums.TemplateStatus;
import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateRequest;
import com.miniESB.dto.template.TemplateResponse;
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
import org.springframework.security.core.Authentication;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTemplateController — unit tests")
class AdminTemplateControllerTest {

    @Mock  private TemplateService templateService;
    @InjectMocks private AdminTemplateController adminTemplateController;

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
    private final TemplateResponse draftDto = new TemplateResponse(
            1L, "My Template", "desc", TemplateType.VALIDATION, validationContent,
            TemplateStatus.DRAFT, 1, null, "admin", LocalDateTime.now(), LocalDateTime.now());

    private final TemplateResponse publishedDto = new TemplateResponse(
            1L, "My Template", "desc", TemplateType.VALIDATION, validationContent,
            TemplateStatus.PUBLISHED, 1, null, "admin", LocalDateTime.now(), LocalDateTime.now());

    private final TemplateResponse disabledDto = new TemplateResponse(
            1L, "My Template", "desc", TemplateType.VALIDATION, validationContent,
            TemplateStatus.DISABLED, 1, null, "admin", LocalDateTime.now(), LocalDateTime.now());

    private Authentication auth(String username) {
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(username);
        return a;
    }

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("returns 201 with DRAFT template")
        void create_returns201() {
            TemplateRequest req = new TemplateRequest("My Template", "desc", TemplateType.VALIDATION, validationContent);
            when(templateService.create(req, "admin")).thenReturn(draftDto);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.create(req, auth("admin"));

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().status()).isEqualTo(TemplateStatus.DRAFT);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when admin not found")
        void create_adminNotFound() {
            TemplateRequest req = new TemplateRequest("T", "d", TemplateType.VALIDATION, validationContent);
            when(templateService.create(req, "ghost"))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            assertThatThrownBy(() -> adminTemplateController.create(req, auth("ghost")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {

        @Test @DisplayName("returns 200 with all templates")
        void findAll_returns200() {
            when(templateService.findAll(TemplateType.VALIDATION))
                    .thenReturn(List.of(draftDto, publishedDto));

            ResponseEntity<List<TemplateResponse>> response =
                    adminTemplateController.findAll(TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
        }
    }

    @Nested @DisplayName("findById()")
    class FindById {

        @Test @DisplayName("returns 200 when template found")
        void findById_returns200() {
            when(templateService.findById(1L, TemplateType.VALIDATION)).thenReturn(draftDto);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.findById(1L, TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(1L);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when not found")
        void findById_notFound() {
            when(templateService.findById(99L, TemplateType.VALIDATION))
                    .thenThrow(new ResourceNotFoundException("not found"));

            assertThatThrownBy(() -> adminTemplateController.findById(99L, TemplateType.VALIDATION))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("update()")
    class Update {

        @Test @DisplayName("returns 200 when DRAFT updated in place")
        void update_draft_returns200() {
            TemplateRequest req = new TemplateRequest("Updated", "desc", TemplateType.VALIDATION, validationContent);
            when(templateService.update(1L, TemplateType.VALIDATION, req, "admin")).thenReturn(draftDto);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.update(1L, TemplateType.VALIDATION, req, auth("admin"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test @DisplayName("returns 200 with new version when PUBLISHED template updated")
        void update_published_returnsNewVersion() {
            TemplateRequest req = new TemplateRequest("V2", "desc", TemplateType.VALIDATION, validationContent);
            TemplateResponse newVersion = new TemplateResponse(
                    2L, "V2", "desc", TemplateType.VALIDATION, validationContent,
                    TemplateStatus.DRAFT, 2, 1L, "admin", LocalDateTime.now(), LocalDateTime.now());
            when(templateService.update(1L, TemplateType.VALIDATION, req, "admin")).thenReturn(newVersion);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.update(1L, TemplateType.VALIDATION, req, auth("admin"));

            assertThat(response.getBody().version()).isEqualTo(2);
            assertThat(response.getBody().parentId()).isEqualTo(1L);
            assertThat(response.getBody().status()).isEqualTo(TemplateStatus.DRAFT);
        }

        @Test @DisplayName("propagates IllegalArgumentException when DISABLED template updated")
        void update_disabled_throws() {
            TemplateRequest req = new TemplateRequest("X", "x", TemplateType.VALIDATION, validationContent);
            when(templateService.update(1L, TemplateType.VALIDATION, req, "admin"))
                    .thenThrow(new IllegalArgumentException("Cannot update DISABLED"));

            assertThatThrownBy(() ->
                    adminTemplateController.update(1L, TemplateType.VALIDATION, req, auth("admin")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DISABLED");
        }
    }

    @Nested @DisplayName("publish()")
    class Publish {

        @Test @DisplayName("returns 200 with PUBLISHED template")
        void publish_returns200() {
            when(templateService.publish(1L, TemplateType.VALIDATION)).thenReturn(publishedDto);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.publish(1L, TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().status()).isEqualTo(TemplateStatus.PUBLISHED);
        }

        @Test @DisplayName("propagates IllegalArgumentException on invalid transition")
        void publish_invalidTransition() {
            when(templateService.publish(1L, TemplateType.VALIDATION))
                    .thenThrow(new IllegalArgumentException("Invalid transition"));

            assertThatThrownBy(() -> adminTemplateController.publish(1L, TemplateType.VALIDATION))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("disable()")
    class Disable {

        @Test @DisplayName("returns 200 with DISABLED template")
        void disable_returns200() {
            when(templateService.disable(1L, TemplateType.VALIDATION)).thenReturn(disabledDto);

            ResponseEntity<TemplateResponse> response =
                    adminTemplateController.disable(1L, TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().status()).isEqualTo(TemplateStatus.DISABLED);
        }

        @Test @DisplayName("propagates IllegalArgumentException on invalid transition")
        void disable_invalidTransition() {
            when(templateService.disable(1L, TemplateType.VALIDATION))
                    .thenThrow(new IllegalArgumentException("Invalid transition"));

            assertThatThrownBy(() -> adminTemplateController.disable(1L, TemplateType.VALIDATION))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
