package com.miniESB.dto.pipelineField;

/**
 * Représente une entrée du fichier JSON de schéma importé.
 *
 * <p>Le type est volontairement une {@link String} (et non {@code FieldType})
 * afin de pouvoir valider chaque entrée individuellement pendant l'import et
 * retourner une erreur ciblée pour cette entrée, plutôt que de faire échouer
 * la désérialisation de tout le fichier dès le premier type invalide.</p>
 *
 * <p>Exemple de fichier attendu :</p>
 * <pre>
 * [
 *   { "fieldPath": "customer.email", "fieldType": "STRING",  "required": true },
 *   { "fieldPath": "amount",         "fieldType": "NUMBER",  "required": true },
 *   { "fieldPath": "metadata",       "fieldType": "OBJECT",  "required": false }
 * ]
 * </pre>
 */
public record PipelineFieldImportItem(
        String fieldPath,
        String fieldType,
        boolean required
) {}