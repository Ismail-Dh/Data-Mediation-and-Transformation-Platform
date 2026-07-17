package com.miniESB.service.docker;

/**
 * Résultat de l'exécution d'une commande externe (ex: {@code docker build}).
 *
 * @param exitCode code de sortie du process (0 = succès)
 * @param output   sortie standard + erreur fusionnées, ligne par ligne concaténées
 */
public record CommandResult(int exitCode, String output) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
