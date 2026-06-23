package com.miniESB.dto.docker;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Requête de bump manuel de version.
 *
 * <p>MINOR : "1.2" → "1.3"  (patch remis à 0 au prochain build)
 * <p>MAJOR : "1.2" → "2.0"  (patch remis à 0 au prochain build)
 */
public record VersionBumpRequest(

        @NotNull(message = "Bump type is required")
        BumpType type,

        /**
         * Optionnel — si fourni, override le calcul automatique.
         * Format attendu : "X.Y" (ex : "3.0")
         */
        @Pattern(regexp = "^\\d+\\.\\d+$",
                 message = "Version must follow the format X.Y (e.g. 1.2)")
        String targetVersion

) {
    public enum BumpType { MINOR, MAJOR }
}