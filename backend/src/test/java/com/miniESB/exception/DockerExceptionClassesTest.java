package com.miniESB.exception;

import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour {@link DockerDaemonException} et {@link DockerBuildException}.
 *
 * <p>Vérifie les contrats de la tâche error-handling :
 * <ul>
 *   <li>Les exceptions portent les informations techniques attendues par le GlobalExceptionHandler.</li>
 *   <li>Elles sont des {@link RuntimeException} (pas de checked throws nécessaire).</li>
 *   <li>Elles sont annotées avec le bon code HTTP.</li>
 * </ul>
 */
@DisplayName("Docker exception classes — unit tests")
class DockerExceptionClassesTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  DockerDaemonException
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DockerDaemonException")
    class DockerDaemonExceptionTest {

        @Test
        @DisplayName("stores message and technicalDetail (String constructor)")
        void constructor_stringDetail_storesFields() {
            DockerDaemonException ex = new DockerDaemonException(
                    "Docker daemon is not reachable",
                    "Cannot connect to the Docker daemon at unix:///var/run/docker.sock");

            assertThat(ex.getMessage()).isEqualTo("Docker daemon is not reachable");
            assertThat(ex.getTechnicalDetail())
                    .isEqualTo("Cannot connect to the Docker daemon at unix:///var/run/docker.sock");
        }

        @Test
        @DisplayName("stores message and extracts detail from Throwable cause")
        void constructor_throwableCause_extractsDetail() {
            IOException ioex = new IOException("Connection refused: /var/run/docker.sock");
            DockerDaemonException ex = new DockerDaemonException(
                    "Failed to start docker build process", ioex);

            assertThat(ex.getMessage()).isEqualTo("Failed to start docker build process");
            assertThat(ex.getTechnicalDetail())
                    .isEqualTo("Connection refused: /var/run/docker.sock");
            assertThat(ex.getCause()).isSameAs(ioex);
        }

        @Test
        @DisplayName("technicalDetail is null when cause has no message")
        void constructor_throwableWithNullMessage_technicalDetailIsNull() {
            IOException ioexNoMsg = new IOException();   // pas de message
            DockerDaemonException ex = new DockerDaemonException("msg", ioexNoMsg);

            assertThat(ex.getTechnicalDetail()).isNull();
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertThat(new DockerDaemonException("msg", "detail"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("is annotated @ResponseStatus(503 SERVICE_UNAVAILABLE)")
        void hasResponseStatus503() {
            ResponseStatus annotation = DockerDaemonException.class
                    .getAnnotation(ResponseStatus.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DockerBuildException
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DockerBuildException")
    class DockerBuildExceptionTest {

        private static final String SAMPLE_LOG =
                "Step 1/3 : FROM mini-esb-backend:latest\n" +
                        "ERROR [internal] load build definition from Dockerfile\n" +
                        "failed to solve: failed to read dockerfile: open Dockerfile: no such file or directory";

        @Test
        @DisplayName("stores message, exitCode and buildLog correctly")
        void constructor_storesAllFields() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 1", 1, SAMPLE_LOG);

            assertThat(ex.getMessage()).isEqualTo("docker build failed with exit code: 1");
            assertThat(ex.getExitCode()).isEqualTo(1);
            assertThat(ex.getBuildLog()).isEqualTo(SAMPLE_LOG);
        }

        @Test
        @DisplayName("preserves exit code 2 (e.g. invalid Dockerfile syntax)")
        void constructor_exitCode2() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 2", 2, "syntax error");

            assertThat(ex.getExitCode()).isEqualTo(2);
        }

        @Test
        @DisplayName("accepts null buildLog without NPE")
        void constructor_nullBuildLog_noPpe() {
            assertThatCode(() -> new DockerBuildException("msg", 1, null))
                    .doesNotThrowAnyException();

            DockerBuildException ex = new DockerBuildException("msg", 1, null);
            assertThat(ex.getBuildLog()).isNull();
        }

        @Test
        @DisplayName("accepts empty buildLog")
        void constructor_emptyBuildLog() {
            DockerBuildException ex = new DockerBuildException("msg", 1, "");
            assertThat(ex.getBuildLog()).isEmpty();
        }

        @Test
        @DisplayName("is a RuntimeException")
        void isRuntimeException() {
            assertThat(new DockerBuildException("msg", 1, "log"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("is annotated @ResponseStatus(422 UNPROCESSABLE_ENTITY)")
        void hasResponseStatus422() {
            ResponseStatus annotation = DockerBuildException.class
                    .getAnnotation(ResponseStatus.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("buildLog contains the full docker output (multiline)")
        void buildLog_isMultilinePreserved() {
            DockerBuildException ex = new DockerBuildException(
                    "docker build failed with exit code: 1", 1, SAMPLE_LOG);

            assertThat(ex.getBuildLog()).contains("Step 1/3");
            assertThat(ex.getBuildLog()).contains("ERROR");
            assertThat(ex.getBuildLog()).contains("no such file or directory");
            // Vérifie que les sauts de ligne sont préservés
            assertThat(ex.getBuildLog().split("\n")).hasSizeGreaterThan(1);
        }
    }
}