package fr.octogenere.report.latex;

import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.report.ReportException;
import fr.octogenere.report.ReportGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Génère un document LaTeX à partir de résultats Java validés. */
public class LatexReportGenerator implements ReportGenerator {
    private static final String TEMPLATE_RESOURCE = "/report/report-template.tex";
    private static final String LOGO_RESOURCE = "/report/uca-logo.png";
    private static final String REPORT_FILE_NAME = "rapport-evaluation.tex";
    private static final String LOGO_FILE_NAME = "uca-logo.png";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH);

    @Override
    public Path generate(EvaluationReport report, Path outputDirectory) {
        if (report == null) {
            throw new ReportException("Les données du rapport ne doivent pas être absentes.");
        }
        if (outputDirectory == null) {
            throw new ReportException("Le dossier de sortie ne doit pas être absent.");
        }

        try {
            Files.createDirectories(outputDirectory);
            copyLogo(outputDirectory);

            String document = render(report);
            Path reportPath = outputDirectory.resolve(REPORT_FILE_NAME);
            Files.writeString(reportPath, document, StandardCharsets.UTF_8);
            return reportPath;
        } catch (IOException error) {
            throw new ReportException("Impossible de générer le rapport LaTeX.", error);
        }
    }

    private String render(EvaluationReport report) throws IOException {
        String document = readResource(TEMPLATE_RESOURCE);
        document = replaceMarker(document, "@@PROJECT_NAME@@", LatexEscaper.escape(report.projectName()));
        document = replaceMarker(document, "@@ANALYSIS_DATE@@", DATE_FORMAT.format(report.analysisDate()));
        document = replaceMarker(document, "@@CONFIGURATION@@", LatexEscaper.escape(report.configuration()));
        document = replaceMarker(document, "@@MODEL_NAME@@", LatexEscaper.escape(report.modelName()));
        document = replaceMarker(document, "@@OVERALL_SUMMARY@@", LatexEscaper.escape(report.overallSummary()));
        document = replaceMarker(document, "@@SUMMARY_ROWS@@", buildSummaryRows(report.criterionResults()));
        return replaceMarker(document, "@@CRITERION_SECTIONS@@", buildCriterionSections(report.criterionResults()));
    }

    private String buildSummaryRows(List<CriterionResult> results) {
        StringBuilder rows = new StringBuilder();
        for (CriterionResult result : results) {
            int percentage = Math.round((result.score() * 100.0f) / result.maxScore());
            rows.append(LatexEscaper.escape(result.criterion()))
                    .append(" & ")
                    .append(result.score())
                    .append(" & ")
                    .append(result.maxScore())
                    .append(" & ")
                    .append(percentage)
                    .append("\\% \\\\\n");
        }
        return rows.toString();
    }

    private String buildCriterionSections(List<CriterionResult> results) {
        StringBuilder sections = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            CriterionResult result = results.get(index);
            sections.append("\\section{")
                    .append(LatexEscaper.escape(result.criterion()))
                    .append("}\n\n")
                    .append("\\scorebox{Score obtenu}{")
                    .append(result.score())
                    .append(" / ")
                    .append(result.maxScore())
                    .append("}\n\n")
                    .append("\\subsection*{Synthèse}\n")
                    .append(LatexEscaper.escape(result.summary()))
                    .append("\n\n");

            appendList(sections, "Points forts", result.strengths());
            appendList(sections, "Faiblesses", result.weaknesses());
            appendList(sections, "Problèmes identifiés", result.issues());
            appendList(sections, "Recommandations", result.recommendations());

            if (index < results.size() - 1) {
                sections.append("\\newpage\n");
            }
        }
        return sections.toString();
    }

    private void appendList(StringBuilder document, String title, List<String> values) {
        document.append("\\subsection*{").append(title).append("}\n");
        if (values.isEmpty()) {
            document.append("\\textit{Aucun élément signalé.}\n\n");
            return;
        }

        document.append("\\begin{itemize}\n");
        for (String value : values) {
            document.append("  \\item ").append(LatexEscaper.escape(value)).append("\n");
        }
        document.append("\\end{itemize}\n\n");
    }

    private String replaceMarker(String template, String marker, String value) {
        if (!template.contains(marker)) {
            throw new ReportException("Le template LaTeX ne contient pas le marqueur " + marker + ".");
        }
        return template.replace(marker, value);
    }

    private String readResource(String resourceName) throws IOException {
        try (InputStream input = LatexReportGenerator.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new ReportException("La ressource " + resourceName + " est introuvable.");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void copyLogo(Path outputDirectory) throws IOException {
        try (InputStream input = LatexReportGenerator.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (input == null) {
                throw new ReportException("Le logo de l'université est introuvable.");
            }
            Files.copy(input, outputDirectory.resolve(LOGO_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
