package fr.octogenere.prompt;

import com.google.gson.JsonObject;

import fr.octogenere.prompt.xml.XmlFileTreeBuilder;

public class PromptBuilder {
    public static String BuildReviewPrompt(String folderPath, JsonObject criteria) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert software architect and senior code reviewer.\n");
        prompt.append("Please perform a comprehensive code review on the provided project.\n\n");

        prompt.append("### IMPORTANT CONTEXT REGARDING THE CODE FORMAT:\n");
        prompt.append("The codebase below is represented in an XML tree format strictly as a way to transmit multiple files within this single prompt. ");
        prompt.append("Assume the project uses a standard, idiomatic directory structure (e.g., Maven, Gradle) in its original form. ");
        prompt.append("Do NOT review, penalize, or comment on the XML structure itself. Focus strictly on the logic, architecture, and syntax of the actual source code contained *inside* the XML nodes.\n\n");

        prompt.append("### CRITERIA TO EVALUATE ON:\n");
        prompt.append(PromptBuilder._BuildCriteriaSubprompt(criteria));

        prompt.append("\n\n------- CODE START -------\n");
        prompt.append(XmlFileTreeBuilder.BuildXmlFileTree(folderPath));
        prompt.append("---- CODE FINISH -----\n");

        return prompt.toString();
    }

    private static String _BuildCriteriaSubprompt(JsonObject criteria) {
        String criteriaSubprompt = "Evaluate based on the list of the following critieria : ";
        for (String name : criteria.keySet()) {
            JsonObject obj = criteria.getAsJsonObject(name);

            boolean enabled = obj.has("enabled") ? obj.get("enabled").getAsBoolean(): false;
            if (!enabled) continue;

            String weight, maxScore, label;
            try {
                weight = Float.toString(obj.get("weight").getAsFloat());
                maxScore = Float.toString(obj.get("maxScore").getAsInt());
                label = obj.get("label").getAsString();
            } catch (NullPointerException e) {
                throw new IllegalStateException("One of the properties of the criterium " + name + " is missing. Please refactor config file thoroughly asap.");
            }

            String criteriumSubprompt = label;
            criteriumSubprompt += " rated on a scale from 0 to " + maxScore;
            criteriumSubprompt += " that accounts for the final score with a weight of " + weight + ", ";

            criteriaSubprompt += criteriumSubprompt;
        }

        return criteriaSubprompt;
    }
}
