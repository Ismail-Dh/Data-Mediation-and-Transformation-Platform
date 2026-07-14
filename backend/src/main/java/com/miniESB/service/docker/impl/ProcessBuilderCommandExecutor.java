package com.miniESB.service.docker.impl;

import com.miniESB.service.docker.CommandExecutor;
import com.miniESB.service.docker.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implémentation par défaut de {@link CommandExecutor}, basée sur {@link ProcessBuilder}.
 * Seule classe du projet à instancier directement {@code ProcessBuilder} — toutes
 * les autres couches dépendent de l'abstraction {@link CommandExecutor}.
 */
@Slf4j
@Component
public class ProcessBuilderCommandExecutor implements CommandExecutor {

    @Override
    public CommandResult run(List<String> command) throws IOException, InterruptedException {
        return run(command, line -> log.debug("[{}] {}", command.get(0), line));
    }

    @Override
    public CommandResult run(List<String> command, Consumer<String> lineConsumer)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        StringBuilder fullOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullOutput.append(line).append("\n");
                lineConsumer.accept(line);
            }
        }

        int exitCode = process.waitFor();
        return new CommandResult(exitCode, fullOutput.toString());
    }

    @Override
    public CommandResult runWithStdin(List<String> command, String stdin)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        process.getOutputStream().write(stdin.getBytes());
        process.getOutputStream().close();

        StringBuilder fullOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullOutput.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        return new CommandResult(exitCode, fullOutput.toString());
    }

    @Override
    public byte[] runForBytes(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        byte[] bytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(String.join(" ", command) + " failed with exit code: " + exitCode);
        }
        return bytes;
    }
}
