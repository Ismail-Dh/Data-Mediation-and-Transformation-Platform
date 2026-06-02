/*package com.miniESB.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.MappingRule;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.impl.MappingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MappingServiceImpl")
class MappingServiceImplTest {

    @Mock private MappingRuleRepository mappingRuleRepository;
    @Mock private PipelineRepository    pipelineRepository;

    private MappingServiceImpl mappingService;

    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        // ObjectMapper injecté manuellement — pas de Spring context
        mappingService = new MappingServiceImpl(
                mappingRuleRepository,
                pipelineRepository,
                new ObjectMapper()
        );

        pipeline = Pipeline.builder().id(1L).build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MappingRule rule(MappingType type, String src, String tgt, String expr) {
        return MappingRule.builder()
                .id(1L)
                .mappingType(type)
                .sourceField(src)
                .targetField(tgt)
                .expression(expr)
                .active(true)
                .pipeline(pipeline)
                .build();
    }

    // =========================================================================
    // createRule()
    // =========================================================================

    @Nested
    @DisplayName("createRule()")
    class CreateRule {

        @Test
        @DisplayName("creates and returns rule when pipeline exists")
        void createRule_success() {
            MappingRuleRequest req = new MappingRuleRequest(
                    "client_id", "id_client", MappingType.FIELD_PLACEMENT, null);

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.save(any(MappingRule.class)))
                    .thenReturn(rule(MappingType.FIELD_PLACEMENT, "client_id", "id_client", null));

            MappingRuleResponse result = mappingService.createRule(1L, req);

            assertThat(result.sourceField()).isEqualTo("client_id");
            assertThat(result.targetField()).isEqualTo("id_client");
            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline not found")
        void createRule_pipelineNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mappingService.createRule(99L,
                    new MappingRuleRequest("src", "tgt", MappingType.FIELD_PLACEMENT, null)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(mappingRuleRepository, never()).save(any());
        }
    }

    // =========================================================================
    // deleteRule()
    // =========================================================================

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("soft-deletes rule when it belongs to the pipeline")
        void deleteRule_success() {
            MappingRule r = rule(MappingType.FIELD_PLACEMENT, "client_id", "id_client", null);
            when(mappingRuleRepository.findById(1L)).thenReturn(Optional.of(r));

            mappingService.deleteRule(1L, 1L);

            assertThat(r.isActive()).isFalse();
            verify(mappingRuleRepository).save(r);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void deleteRule_notFound() {
            when(mappingRuleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mappingService.deleteRule(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule belongs to another pipeline")
        void deleteRule_wrongPipeline() {
            Pipeline other = Pipeline.builder().id(2L).build();
            MappingRule r = MappingRule.builder()
                    .id(1L).active(true).pipeline(other)
                    .mappingType(MappingType.FIELD_PLACEMENT)
                    .sourceField("src").targetField("tgt").build();

            when(mappingRuleRepository.findById(1L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> mappingService.deleteRule(1L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(mappingRuleRepository, never()).save(any());
        }
    }

    // =========================================================================
    // activateRule()
    // =========================================================================

    @Nested
    @DisplayName("activateRule()")
    class ActivateRule {

        @Test
        @DisplayName("re-activates a disabled rule")
        void activateRule_success() {
            MappingRule r = rule(MappingType.FIELD_PLACEMENT, "client_id", "id_client", null);
            r.setActive(false);
            when(mappingRuleRepository.findById(1L)).thenReturn(Optional.of(r));
            when(mappingRuleRepository.save(any())).thenReturn(r);

            MappingRuleResponse result = mappingService.activateRule(1L, 1L);

            assertThat(r.isActive()).isTrue();
            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule belongs to another pipeline")
        void activateRule_wrongPipeline() {
            Pipeline other = Pipeline.builder().id(2L).build();
            MappingRule r = MappingRule.builder()
                    .id(1L).active(false).pipeline(other)
                    .mappingType(MappingType.FIELD_PLACEMENT)
                    .sourceField("src").targetField("tgt").build();

            when(mappingRuleRepository.findById(1L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> mappingService.activateRule(1L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // applyMappingToPayload() — FIELD_PLACEMENT
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — FIELD_PLACEMENT")
    class FieldPlacement {

        @Test
        @DisplayName("renames field and keeps unmapped fields unchanged")
        void fieldPlacement_renamesField() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FIELD_PLACEMENT, "client_id", "id_client", null)));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"client_id\": \"123\", \"montant\": 500}");

            assertThat(result.mapped()).containsKey("id_client");
            assertThat(result.mapped()).doesNotContainKey("client_id");
            assertThat(result.mapped().get("id_client")).isEqualTo("123");
            assertThat(result.mapped()).containsKey("montant"); // champ sans règle passe tel quel
        }

        @Test
        @DisplayName("skips rule when sourceField not found in payload")
        void fieldPlacement_sourceFieldMissing() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FIELD_PLACEMENT, "missing_field", "tgt", null)));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"client_id\": \"123\"}");

            assertThat(result.mapped()).doesNotContainKey("tgt");
            assertThat(result.mapped()).containsKey("client_id");
        }
    }

    // =========================================================================
    // applyMappingToPayload() — VALUE_TRANSFORM
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — VALUE_TRANSFORM")
    class ValueTransform {

        @Test
        @DisplayName("UPPERCASE transforms string to upper case")
        void valueTransform_uppercase() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.VALUE_TRANSFORM, "nom", "nom", "UPPERCASE")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"nom\": \"john doe\"}");

            assertThat(result.mapped().get("nom")).isEqualTo("JOHN DOE");
        }

        @Test
        @DisplayName("LOWERCASE transforms string to lower case")
        void valueTransform_lowercase() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.VALUE_TRANSFORM, "nom", "nom", "LOWERCASE")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"nom\": \"JOHN DOE\"}");

            assertThat(result.mapped().get("nom")).isEqualTo("john doe");
        }

        @Test
        @DisplayName("CONCAT joins multiple fields with separator")
        void valueTransform_concat() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.VALUE_TRANSFORM, "firstName", "fullName",
                            "CONCAT: :firstName:lastName")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"firstName\": \"John\", \"lastName\": \"Doe\"}");

            assertThat(result.mapped().get("fullName")).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("SPLIT extracts part of a string by separator and index")
        void valueTransform_split() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.VALUE_TRANSFORM, "email", "username", "SPLIT:@:0")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"email\": \"john@example.com\"}");

            assertThat(result.mapped().get("username")).isEqualTo("john");
        }

        @Test
        @DisplayName("REGEX_REPLACE removes non-numeric characters")
        void valueTransform_regexReplace() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.VALUE_TRANSFORM, "phone", "phone",
                            "REGEX_REPLACE:[^0-9]:")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"phone\": \"+33 6 00 11 22 33\"}");

            assertThat(result.mapped().get("phone")).isEqualTo("33600112233");
        }
    }

    // =========================================================================
    // applyMappingToPayload() — FORMAT_CHANGE
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — FORMAT_CHANGE")
    class FormatChange {

        @Test
        @DisplayName("STRING_TO_INT converts string number to integer")
        void formatChange_stringToInt() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FORMAT_CHANGE, "age", "age", "STRING_TO_INT")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"age\": \"25\"}");

            assertThat(result.mapped().get("age")).isEqualTo(25);
        }

        @Test
        @DisplayName("STRING_TO_DOUBLE converts string to double")
        void formatChange_stringToDouble() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FORMAT_CHANGE, "prix", "prix", "STRING_TO_DOUBLE")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"prix\": \"99.99\"}");

            assertThat(result.mapped().get("prix")).isEqualTo(99.99);
        }

        @Test
        @DisplayName("date reformatting converts from dd/MM/yyyy to yyyy-MM-dd")
        void formatChange_dateReformat() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FORMAT_CHANGE, "dateNaissance", "dateNaissance",
                            "dd/MM/yyyy|yyyy-MM-dd")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"dateNaissance\": \"15/01/1990\"}");

            assertThat(result.mapped().get("dateNaissance")).isEqualTo("1990-01-15");
        }

        @Test
        @DisplayName("DATE_TO_UNIX converts ISO date to epoch seconds")
        void formatChange_dateToUnix() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FORMAT_CHANGE, "date", "date", "DATE_TO_UNIX")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"date\": \"1990-01-15\"}");

            assertThat(result.mapped().get("date")).isNotNull();
            assertThat((Long) result.mapped().get("date")).isPositive();
        }
    }

    // =========================================================================
    // applyMappingToPayload() — CALCULATED_FIELD
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — CALCULATED_FIELD")
    class CalculatedField {

        @Test
        @DisplayName("arithmetic expression computes correct result")
        void calculatedField_arithmetic() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "totalTTC",
                            "{prix} * (1 + {tva})")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"prix\": 100, \"tva\": 0.2}");

            assertThat((Double) result.mapped().get("totalTTC")).isEqualTo(120.0);
        }

        @Test
        @DisplayName("IF returns VIP when condition is true")
        void calculatedField_conditional_vip() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "categorie",
                            "IF:montant:gt:1000:VIP:STD")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"montant\": 1500}");

            assertThat(result.mapped().get("categorie")).isEqualTo("VIP");
        }

        @Test
        @DisplayName("IF returns STD when condition is false")
        void calculatedField_conditional_std() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "categorie",
                            "IF:montant:gt:1000:VIP:STD")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"montant\": 500}");

            assertThat(result.mapped().get("categorie")).isEqualTo("STD");
        }

        @Test
        @DisplayName("SUM aggregates numeric values from array")
        void calculatedField_sum() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "total",
                            "SUM:items[].prix")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"items\": [{\"prix\": 100}, {\"prix\": 200}, {\"prix\": 50}]}");

            assertThat((Double) result.mapped().get("total")).isEqualTo(350.0);
        }

        @Test
        @DisplayName("AVG computes average of array values")
        void calculatedField_avg() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "moyenne",
                            "AVG:items[].prix")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"items\": [{\"prix\": 100}, {\"prix\": 200}]}");

            assertThat((Double) result.mapped().get("moyenne")).isEqualTo(150.0);
        }

        @Test
        @DisplayName("COUNT returns number of items in array")
        void calculatedField_count() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.CALCULATED_FIELD, "N/A", "nbItems",
                            "COUNT:items[]")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"items\": [{\"prix\": 100}, {\"prix\": 200}, {\"prix\": 50}]}");

            assertThat(result.mapped().get("nbItems")).isEqualTo(3);
        }
    }

    // =========================================================================
    // applyMappingToPayload() — RESTRUCTURING
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — RESTRUCTURING")
    class Restructuring {

        @Test
        @DisplayName("NEST moves flat field to nested path")
        void restructuring_nest() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.RESTRUCTURING, "mail", "contact.mail", null)));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"mail\": \"john@example.com\"}");

            assertThat(result.mapped()).doesNotContainKey("mail");
            assertThat(result.mapped()).containsKey("contact");

            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) result.mapped().get("contact");
            assertThat(contact.get("mail")).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("FLATTEN explodes nested object to flat keys")
        void restructuring_flatten() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.RESTRUCTURING, "contact", "N/A", "FLATTEN")));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"contact\": {\"mail\": \"john@example.com\", \"phone\": \"0600\"}}");

            assertThat(result.mapped()).doesNotContainKey("contact");
            assertThat(result.mapped()).containsKey("contact.mail");
            assertThat(result.mapped()).containsKey("contact.phone");
            assertThat(result.mapped().get("contact.mail")).isEqualTo("john@example.com");
            assertThat(result.mapped().get("contact.phone")).isEqualTo("0600");
        }
    }

    // =========================================================================
    // applyMappingToPayload() — cas limites
    // =========================================================================

    @Nested
    @DisplayName("applyMappingToPayload() — edge cases")
    class EdgeCases {

        @Test
        @DisplayName("returns input as-is when no rules defined")
        void noRules_returnsInputAsIs() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of());

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"client_id\": \"123\"}");

            assertThat(result.mapped()).isEqualTo(result.original());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when rawContent is not valid JSON")
        void invalidJson_throwsException() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            

            assertThatThrownBy(() -> mappingService.applyMappingToPayload(1L, "not a json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON content");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline not found")
        void pipelineNotFound_throwsException() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mappingService.applyMappingToPayload(99L, "{\"key\": \"val\"}"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("original payload is never modified — only mapped is transformed")
        void originalPayload_isNeverModified() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L))
                    .thenReturn(List.of(rule(MappingType.FIELD_PLACEMENT, "client_id", "id_client", null)));

            MappingResultResponse result = mappingService.applyMappingToPayload(
                    1L, "{\"client_id\": \"123\"}");

            assertThat(result.original()).containsKey("client_id");
            assertThat(result.mapped()).containsKey("id_client");
            assertThat(result.original()).doesNotContainKey("id_client");
        }
    }
}*/