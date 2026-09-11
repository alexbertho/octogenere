package fr.octogenere.prompt;

import com.google.gson.JsonObject;

import fr.octogenere.prompt.xml.XmlFileTreeBuilder;

public class PromptBuilder {
    public static String BuildReviewPrompt(String folderPath, JsonObject criteria) throws Exception {
        String prompt = "Generate a full review of this XML-packed code.\n";

        prompt += PromptBuilder._BuildCriteriaSubprompt(criteria);

        prompt += "\n\n------- CODE START -------\n";
        prompt += XmlFileTreeBuilder.BuildXmlFileTree(folderPath);
        prompt += "---- CODE FINISH -----\n";

        return prompt;
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
