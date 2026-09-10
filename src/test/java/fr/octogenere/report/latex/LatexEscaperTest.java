package fr.octogenere.report.latex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LatexEscaperTest {
    @Test
    void escapesEveryLatexSpecialCharacter() {
        assertEquals(
                "\\textbackslash{} \\{ \\} \\$ \\& \\# \\_ \\% \\textasciitilde{} \\textasciicircum{}",
                LatexEscaper.escape("\\ { } $ & # _ % ~ ^"));
    }

    @Test
    void neutralizesALatexCommand() {
        String escaped = LatexEscaper.escape("\\input{/etc/passwd}");

        assertFalse(escaped.contains("\\input{"));
        assertEquals("\\textbackslash{}input\\{/etc/passwd\\}", escaped);
    }
}
