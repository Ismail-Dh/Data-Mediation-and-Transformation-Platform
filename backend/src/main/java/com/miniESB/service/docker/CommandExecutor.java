package com.miniESB.service.docker;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstraction de l'exécution d'une commande externe (typiquement le CLI Docker).
 *
 * <p>Avant ce refactoring, {@code DockerImageGeneratorService} et
 * {@code DockerImagePushService} instanciaient directement {@code new ProcessBuilder(...)},
 * ce qui violait le Dependency Inversion Principle : la couche service dépendait
 * d'un détail concret (le système d'exploitation) plutôt que d'une abstraction,
 * rendant ces classes impossibles à tester sans un vrai daemon Docker.</p>
 *
 * <p>Avec cette interface, les services dépendent de {@code CommandExecutor}
 * et peuvent être testés avec un mock, sans jamais lancer de process réel.</p>
 */
public interface CommandExecutor {

    /** Exécute la commande et attend sa fin, sans traiter la sortie ligne à ligne. */
    CommandResult run(List<String> command) throws IOException, InterruptedException;

    /**
     * Exécute la commande en appelant {@code lineConsumer} pour chaque ligne de sortie
     * au fur et à mesure (utile pour le streaming SSE d'un build Docker).
     */
    CommandResult run(List<String> command, Consumer<String> lineConsumer) throws IOException, InterruptedException;

    /** Exécute la commande en écrivant {@code stdin} sur l'entrée standard du process (ex: docker login --password-stdin). */
    CommandResult runWithStdin(List<String> command, String stdin) throws IOException, InterruptedException;

    /** Exécute la commande et retourne sa sortie standard brute (ex: docker save). */
    byte[] runForBytes(List<String> command) throws IOException, InterruptedException;
}
