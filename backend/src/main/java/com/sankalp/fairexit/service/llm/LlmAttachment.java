package com.sankalp.fairexit.service.llm;

/** A file sent with the prompt: a photo of a bill, or a scanned PDF. */
public record LlmAttachment(String mimeType, byte[] data) {
}
