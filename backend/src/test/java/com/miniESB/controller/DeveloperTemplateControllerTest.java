package com.miniESB.controller;


import com.miniESB.domain.enums.TemplateStatus;
import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateResponse;
import com.miniESB.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)
@DisplayName("DeveloperTemplateController — unit tests")
class DeveloperTemplateControllerTest {

    @Mock  private TemplateService templateService;
    @InjectMocks private DeveloperTemplateController controller;
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
    private final TemplateResponse publishedDto = new TemplateResponse(
            1L, "Published Template", "desc", TemplateType.VALIDATION, validationContent,
            TemplateStatus.PUBLISHED, 1, null, "admin",
            LocalDateTime.now(), LocalDateTime.now());

    @Nested
    @DisplayName("findPublished()")
    class FindPublished {

        @Test
        @DisplayName("returns 200 with only PUBLISHED validation templates")
        void findPublished_validation_returns200() {
            when(templateService.findPublished(TemplateType.VALIDATION))
                    .thenReturn(List.of(publishedDto));

            ResponseEntity<List<TemplateResponse>> response =
                    controller.findPublished(TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).status()).isEqualTo(TemplateStatus.PUBLISHED);
        }

        @Test
        @DisplayName("returns 200 with only PUBLISHED mapping templates")
        void findPublished_mapping_returns200() {
            TemplateResponse mappingDto = new TemplateResponse(
                    2L, "Mapping", "desc", TemplateType.MAPPING, mappingContent,
                    TemplateStatus.PUBLISHED, 1, null, "admin",
                    LocalDateTime.now(), LocalDateTime.now());

            when(templateService.findPublished(TemplateType.MAPPING))
                    .thenReturn(List.of(mappingDto));

            ResponseEntity<List<TemplateResponse>> response =
                    controller.findPublished(TemplateType.MAPPING);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get(0).type()).isEqualTo(TemplateType.MAPPING);
        }

        @Test
        @DisplayName("returns 200 with empty list when no published templates")
        void findPublished_empty() {
            when(templateService.findPublished(TemplateType.VALIDATION)).thenReturn(List.of());

            ResponseEntity<List<TemplateResponse>> response =
                    controller.findPublished(TemplateType.VALIDATION);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }
    }
}