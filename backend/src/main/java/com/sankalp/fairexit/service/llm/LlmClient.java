package com.sankalp.fairexit.service.llm;

import java.util.List;

/**
 * The only door to an LLM. Gemini sits behind it today; another provider can be
 * swapped in by writing one more class. The LLM only reads. It never decides.
 */
public interface LlmClient {

    /**
     * @param instruction what to do and the JSON shape to answer in
     * @param text        the user's text, already masked
     * @param attachments files to read with the text (may be empty)
     * @return the model's answer as JSON text (not yet checked)
     * @throws LlmException when the call cannot be made or fails
     */
    String generateJson(String instruction, String text, List<LlmAttachment> attachments);

    default String generateJson(String instruction, String text) {
        return generateJson(instruction, text, List.of());
    }
}
