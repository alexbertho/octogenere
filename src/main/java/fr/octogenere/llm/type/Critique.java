package fr.octogenere.llm.type;

import java.util.List;

public class Critique {
    private String criterion;
    private int score;
    private int maxScore;
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> issues;
    private List<String> recommendations;
}
