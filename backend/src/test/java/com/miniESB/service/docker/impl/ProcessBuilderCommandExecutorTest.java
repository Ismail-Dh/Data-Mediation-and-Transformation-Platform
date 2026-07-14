package com.miniESB.service.docker.impl;

import com.miniESB.service.docker.CommandResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * These tests exercise the executor against real, harmless OS commands
 * (echo/cat/sh) instead of Docker, keeping them fast and hermetic.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
@DisplayName("ProcessBuilderCommandExecutor — unit tests")
class ProcessBuilderCommandExecutorTest {

    private final ProcessBuilderCommandExecutor executor = new ProcessBuilderCommandExecutor();

    @Test
    @DisplayName("run() captures stdout and a zero exit code on success")
    void runCapturesOutputAndExitCode() throws Exception {
        CommandResult result = executor.run(List.of("echo", "hello-world"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("hello-world");
    }

    @Test
    @DisplayName("run() returns a non-zero exit code for a failing command")
    void runReturnsNonZeroExitCode() throws Exception {
        CommandResult result = executor.run(List.of("sh", "-c", "exit 3"));

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("run() with a line consumer invokes it for each output line")
    void runWithLineConsumer() throws Exception {
        List<String> lines = new ArrayList<>();
        executor.run(List.of("sh", "-c", "echo line1; echo line2"), lines::add);

        assertThat(lines).containsExactly("line1", "line2");
    }

    @Test
    @DisplayName("runWithStdin() feeds stdin to the process and captures its output")
    void runWithStdinFeedsInput() throws Exception {
        CommandResult result = executor.runWithStdin(List.of("cat"), "piped-input");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("piped-input");
    }

    @Test
    @DisplayName("runForBytes() returns the raw stdout bytes on success")
    void runForBytesReturnsRawOutput() throws Exception {
        byte[] bytes = executor.runForBytes(List.of("echo", "-n", "raw-bytes"));

        assertThat(new String(bytes)).isEqualTo("raw-bytes");
    }

    @Test
    @DisplayName("runForBytes() throws an IOException when the command fails")
    void runForBytesThrowsOnFailure() {
        assertThatThrownBy(() -> executor.runForBytes(List.of("sh", "-c", "exit 1")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exit code");
    }
}
