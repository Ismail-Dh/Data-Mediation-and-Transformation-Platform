package com.miniESB.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.FieldType;
import com.miniESB.domain.enums.HttpRequestMethod;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ResponseMappingRuleRepository;
import com.miniESB.service.impl.DockerBuildArtifactGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DockerBuildArtifactGenerator}.
 *
 * <p><strong>Hypothèses</strong> : {@code DataFormat.JSON} et
 * {@code HttpRequestMethod.POST} sont supposés exister — à corriger si les
 * constantes réelles diffèrent.</p>
 *
 * <p>Pas de mock du filesystem : {@code buildDir} est un vrai répertoire
 * temporaire JUnit ({@code @TempDir}), et on lit le fichier {@code Dockerfile}
 * réellement écrit pour vérifier son contenu.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DockerBuildArtifactGenerator")
class DockerBuildArtifactGeneratorTest {

    @Mock private MappingRuleRepository mappingRuleRepository;
    @Mock private PipelineFieldRepository pipelineFieldRepository;
    @Mock private PipelineRepository pipelineRepository;
    @Mock private ResponseMappingRuleRepository responseMappingRuleRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ObjectWriter objectWriter;

    @InjectMocks
    private DockerBuildArtifactGenerator generator;

    @TempDir
    Path buildDir;

    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = Pipeline.builder()
                .id(1L)
                .outputFormat(DataFormat.JSON)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  generateRulesJson()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRulesJson")
    class GenerateRulesJson {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void generateRulesJson_unknownPipeline_throwsResourceNotFound() {
            when(pipelineRepository.findByIdWithProviders(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> generator.generateRulesJson(99L, buildDir))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(objectMapper);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("assembles mappingRules, validationFields, providers, and outputFormat correctly")
        void generateRulesJson_fullData_assemblesCorrectMap() throws Exception {
            MappingRule mappingRule = MappingRule.builder()
                    .sourceField("firstName").targetField("first_name")
                    .mappingType(MappingType.FIELD_PLACEMENT).expression(null)
                    .build();
            PipelineField field = PipelineField.builder()
                    .fieldPath("email").fieldType(FieldType.STRING)
                    .required(true).nullable(false)
                    .build();

            Provider provider = mock(Provider.class);
            when(provider.getId()).thenReturn(5L);
            when(provider.getName()).thenReturn("Billing API");
            when(provider.getEndpoint()).thenReturn("https://billing.example.com");
            when(provider.getTimeout()).thenReturn(3000);

            PipelineProvider link = mock(PipelineProvider.class);
            when(link.getProvider()).thenReturn(provider);
            when(link.getHttpMethod()).thenReturn(HttpRequestMethod.POST);

            pipeline.setPipelineProviders(List.of(link));

            when(pipelineRepository.findByIdWithProviders(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of(mappingRule));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));
            when(responseMappingRuleRepository.findByPipelineId(1L)).thenReturn(List.of());
            when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

            generator.generateRulesJson(1L, buildDir);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(objectWriter).writeValue(any(File.class), captor.capture());
            Map<String, Object> rules = captor.getValue();

            assertThat(rules.get("pipelineId")).isEqualTo(1L);
            assertThat(rules.get("outputFormat")).isEqualTo("JSON");

            List<Map<String, Object>> mappingList = (List<Map<String, Object>>) rules.get("mappingRules");
            assertThat(mappingList).hasSize(1);
            assertThat(mappingList.get(0)).containsEntry("sourceField", "firstName")
                    .containsEntry("targetField", "first_name")
                    .containsEntry("mappingType", "FIELD_PLACEMENT");

            List<Map<String, Object>> validationList = (List<Map<String, Object>>) rules.get("validationFields");
            assertThat(validationList).hasSize(1);
            assertThat(validationList.get(0)).containsEntry("fieldPath", "email")
                    .containsEntry("required", true)
                    .containsEntry("nullable", false);

            List<Map<String, Object>> providersList = (List<Map<String, Object>>) rules.get("providers");
            assertThat(providersList).hasSize(1);
            assertThat(providersList.get(0)).containsEntry("id", 5L)
                    .containsEntry("name", "Billing API")
                    .containsEntry("httpMethod", "POST");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("defaults httpMethod to POST when the pipeline-provider link has no method set")
        void generateRulesJson_nullHttpMethod_defaultsToPost() throws Exception {
            Provider provider = mock(Provider.class);
            when(provider.getId()).thenReturn(5L);

            PipelineProvider link = mock(PipelineProvider.class);
            when(link.getProvider()).thenReturn(provider);
            when(link.getHttpMethod()).thenReturn(null);

            pipeline.setPipelineProviders(List.of(link));

            when(pipelineRepository.findByIdWithProviders(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(responseMappingRuleRepository.findByPipelineId(1L)).thenReturn(List.of());
            when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

            generator.generateRulesJson(1L, buildDir);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(objectWriter).writeValue(any(File.class), captor.capture());
            List<Map<String, Object>> providersList =
                    (List<Map<String, Object>>) captor.getValue().get("providers");

            assertThat(providersList.get(0)).containsEntry("httpMethod", "POST");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("produces an empty providers list, without NPE, when pipelineProviders is null")
        void generateRulesJson_nullPipelineProviders_producesEmptyProvidersList() throws Exception {
            pipeline.setPipelineProviders(null);

            when(pipelineRepository.findByIdWithProviders(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(responseMappingRuleRepository.findByPipelineId(1L)).thenReturn(List.of());
            when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

            assertThatCode(() -> generator.generateRulesJson(1L, buildDir)).doesNotThrowAnyException();

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(objectWriter).writeValue(any(File.class), captor.capture());
            assertThat((List<?>) captor.getValue().get("providers")).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("filters out inactive response mapping rules, keeping only active ones")
        void generateRulesJson_responseMappingRules_filtersInactiveOnes() throws Exception {
            ResponseMappingRule activeRule = ResponseMappingRule.builder()
                    .sourceField("status").targetField("orderStatus")
                    .mappingType(MappingType.FIELD_PLACEMENT)
                    .required(true).active(true)
                    .build();
            ResponseMappingRule inactiveRule = ResponseMappingRule.builder()
                    .sourceField("legacy").targetField("legacyOut")
                    .mappingType(MappingType.FIELD_PLACEMENT)
                    .required(false).active(false)
                    .build();

            when(pipelineRepository.findByIdWithProviders(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(responseMappingRuleRepository.findByPipelineId(1L))
                    .thenReturn(List.of(activeRule, inactiveRule));
            when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

            generator.generateRulesJson(1L, buildDir);

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(objectWriter).writeValue(any(File.class), captor.capture());
            List<Map<String, Object>> responseRulesList =
                    (List<Map<String, Object>>) captor.getValue().get("responseMappingRules");

            assertThat(responseRulesList).hasSize(1);
            assertThat(responseRulesList.get(0)).containsEntry("sourceField", "status");
        }

        @Test
        @DisplayName("writes the rules.json file into the given buildDir")
        void generateRulesJson_writesFileAtCorrectLocation() throws Exception {
            when(pipelineRepository.findByIdWithProviders(1L)).thenReturn(Optional.of(pipeline));
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(responseMappingRuleRepository.findByPipelineId(1L)).thenReturn(List.of());
            when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

            generator.generateRulesJson(1L, buildDir);

            ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
            verify(objectWriter).writeValue(fileCaptor.capture(), any());
            assertThat(fileCaptor.getValue().getName()).isEqualTo("rules.json");
            assertThat(fileCaptor.getValue().getParentFile().toPath()).isEqualTo(buildDir);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  generateDockerfile()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateDockerfile")
    class GenerateDockerfile {

        @Test
        @DisplayName("writes a Dockerfile with engine-mode configuration into buildDir")
        void generateDockerfile_writesExpectedContent() throws Exception {
            generator.generateDockerfile(buildDir);

            Path dockerfile = buildDir.resolve("Dockerfile");
            assertThat(Files.exists(dockerfile)).isTrue();

            String content = Files.readString(dockerfile);
            assertThat(content)
                    .contains("FROM mini-esb-backend:latest")
                    .contains("COPY rules.json /app/rules.json")
                    .contains("ENV ENGINE_MODE=true")
                    .contains("--engine.mode=true")
                    .contains("--spring.profiles.active=engine")
                    .contains("EXPOSE 8080");
        }

        @Test
        @DisplayName("overwrites any pre-existing Dockerfile in buildDir")
        void generateDockerfile_overwritesExistingFile() throws Exception {
            Path dockerfile = buildDir.resolve("Dockerfile");
            Files.writeString(dockerfile, "OLD CONTENT — should be replaced");

            generator.generateDockerfile(buildDir);

            String content = Files.readString(dockerfile);
            assertThat(content).doesNotContain("OLD CONTENT");
            assertThat(content).contains("FROM mini-esb-backend:latest");
        }
    }
}