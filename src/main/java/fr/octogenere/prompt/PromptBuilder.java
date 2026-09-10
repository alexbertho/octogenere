package fr.octogenere.prompt;

import fr.octogenere.prompt.xml.XmlFileTreeBuilder;

public class PromptBuilder {
    public static String BuildReviewPrompt(String folderPath) throws Exception {
        String prompt = "Generate a full review of this XML-packed code base according to the following criterium : is it good ?\n\n------- CODE START -------\n";

        String xmlTree = XmlFileTreeBuilder.BuildXmlFileTree(folderPath);
        prompt += xmlTree;

        prompt += "---- CODE FINISH -----\n";

        return prompt;
    }
}
