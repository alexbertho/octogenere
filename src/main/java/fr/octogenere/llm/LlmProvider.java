package fr.octogenere.llm;

public abstract class LlmProvider {
    private String apiKey;
    private String apiModel;

    // The unique schema json in memory
    private static final String SCHEMA = """
            The answer need to be absolutely in JSON format (no bonus, no explicative, no excess) :
            {
                "projectName": "string",
                "analysisDate": "YYYY-MM-DD",
                "configuration": "string",
                "modelName": "string",
                "overallSummary": "string",
                "criteria": [
                    {
                    "criterion": "string",
                    "score": 0,
                    "maxScore": 10,
                    "summary": "string",
                    "strengths": ["string"],
                    "weaknesses": ["string"],
                    "issues": ["string"],
                    "recommendations": ["string"]
                    }
                ]
            }
            """;

    public LlmProvider(String apiKey, String apiModel) {
        this.apiKey = apiKey;
        this.apiModel = apiModel;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getApiModel() {
        return this.apiModel;
    }

    public static String getSCHEMA() {
        return SCHEMA;
    }

    public void setApiKey(String key) {
        this.apiKey = key;
    }

    public void setApiModel(String model) {
        this.apiModel = model;
    }

    public abstract String ask(String prompt);
}
