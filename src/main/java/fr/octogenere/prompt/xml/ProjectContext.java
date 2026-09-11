package fr.octogenere.prompt.xml;

import java.util.List;

/** Contexte projet borné et prêt à être inséré dans un prompt. */
public record ProjectContext(
        String xml,
        int includedFiles,
        int omittedFiles,
        boolean truncated,
        List<String> suspiciousFiles) {

    public ProjectContext {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Le contexte XML ne doit pas être vide.");
        }
        suspiciousFiles = List.copyOf(suspiciousFiles);
    }
}
