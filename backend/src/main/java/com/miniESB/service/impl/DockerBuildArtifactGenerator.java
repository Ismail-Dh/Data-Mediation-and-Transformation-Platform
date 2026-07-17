package com.miniESB.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.MappingRule;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.entity.ResponseMappingRule;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ResponseMappingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Génère les artefacts de build ({@code rules.json}, {@code Dockerfile}) pour
 * une image de pipeline en "mode engine".
 *
 * <p>Extrait de {@code DockerImageGeneratorService}, qui mélangeait cette
 * responsabilité (génération de fichiers) avec l'orchestration du build Docker,
 * la vérification du daemon, le streaming SSE et la persistance — violation SRP.</p>
 *
 * <p>{@code rules.json} embarque désormais aussi les providers du pipeline
 * (avec leur méthode HTTP par lien {@code PipelineProvider}) et les règles de
 * mapping de réponse (T6), pour que l'image Docker autonome puisse dispatcher
 * et valider les réponses sans base de données.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerBuildArtifactGenerator {

    private final MappingRuleRepository mappingRuleRepository;
    private final PipelineFieldRepository pipelineFieldRepository;
    private final PipelineRepository pipelineRepository;
    private final ResponseMappingRuleRepository responseMappingRuleRepository;
    private final ObjectMapper objectMapper;

    /** Génère {@code rules.json} dans {@code buildDir} à partir de la configuration active du pipeline. */
    public void generateRulesJson(Long pipelineId, Path buildDir) throws Exception {
        List<MappingRule> mappingRules = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);
        List<PipelineField> validationFields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        Pipeline pipeline = pipelineRepository.findByIdWithProviders(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found with id=" + pipelineId));

        List<Map<String, Object>> mappingList = mappingRules.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sourceField", r.getSourceField());
                    m.put("targetField", r.getTargetField());
                    m.put("mappingType", r.getMappingType().name());
                    m.put("expression", r.getExpression());
                    return m;
                }).toList();

        List<Map<String, Object>> validationList = validationFields.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fieldPath", f.getFieldPath());
                    m.put("fieldType", f.getFieldType().name());
                    m.put("required", f.isRequired());
                    m.put("nullable", f.isNullable());
                    return m;
                }).toList();

        // Chaque provider embarque sa méthode HTTP (GET/POST/PUT/PATCH), choisie
        // pour CE pipeline via l'association PipelineProvider — l'image Docker
        // générée l'utilise telle quelle pour son dispatch autonome.
        List<Map<String, Object>> providersList = Optional.ofNullable(pipeline.getPipelineProviders())
                .orElse(List.of())
                .stream()
                .map(link -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", link.getProvider().getId());
                    m.put("name", link.getProvider().getName());
                    m.put("endpoint", link.getProvider().getEndpoint());
                    m.put("timeout", link.getProvider().getTimeout());
                    m.put("httpMethod", link.getHttpMethod() != null ? link.getHttpMethod().name() : "POST");
                    return m;
                }).toList();

        List<ResponseMappingRule> responseRules = responseMappingRuleRepository.findByPipelineId(pipelineId);
        List<Map<String, Object>> responseMappingRulesList = responseRules.stream()
                .filter(ResponseMappingRule::isActive)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("providerId", r.getProvider() != null ? r.getProvider().getId() : null);
                    m.put("sourceField", r.getSourceField());
                    m.put("targetField", r.getTargetField());
                    m.put("mappingType", r.getMappingType().name());
                    m.put("expression", r.getExpression());
                    m.put("required", r.isRequired());
                    return m;
                }).toList();

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("pipelineId", pipelineId);
        rules.put("mappingRules", mappingList);
        rules.put("validationFields", validationList);
        rules.put("providers", providersList);
        rules.put("outputFormat", pipeline.getOutputFormat().name());
        rules.put("responseMappingRules", responseMappingRulesList);

        File rulesFile = buildDir.resolve("rules.json").toFile();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(rulesFile, rules);
        log.info("rules.json generated: {}", rulesFile.getAbsolutePath());
    }

    /** Génère le {@code Dockerfile} standard pour une image en mode engine. */
    public void generateDockerfile(Path buildDir) throws Exception {
        String dockerfileContent = """
                FROM mini-esb-backend:latest
                COPY rules.json /app/rules.json
                ENV ENGINE_MODE=true
                ENV RULES_FILE=/app/rules.json
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar", \
                "--engine.mode=true", \
                "--engine.rules-file=/app/rules.json", \
                "--spring.profiles.active=engine"]
                """;
        Files.writeString(buildDir.resolve("Dockerfile"), dockerfileContent);
        log.info("Dockerfile generated");
    }
}
