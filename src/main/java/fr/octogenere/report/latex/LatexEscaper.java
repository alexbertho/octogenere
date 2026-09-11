package fr.octogenere.report.latex;

/** Protège un texte avant son insertion dans un document LaTeX. */
public final class LatexEscaper {
    private LatexEscaper() {
        // Classe utilitaire : elle ne contient aucun état et ne doit pas être instanciée.
    }

    public static String escape(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Le texte à protéger ne doit pas être absent.");
        }

        StringBuilder escaped = new StringBuilder();
        for (char character : text.toCharArray()) {
            switch (character) {
                case '\\' -> escaped.append("\\textbackslash{}");
                case '{' -> escaped.append("\\{");
                case '}' -> escaped.append("\\}");
                case '$' -> escaped.append("\\$");
                case '&' -> escaped.append("\\&");
                case '#' -> escaped.append("\\#");
                case '_' -> escaped.append("\\_");
                case '%' -> escaped.append("\\%");
                case '~' -> escaped.append("\\textasciitilde{}");
                case '^' -> escaped.append("\\textasciicircum{}");
                case '\r' -> {
                    // Les retours Windows sont ignorés ; le caractère \n est conservé.
                }
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
