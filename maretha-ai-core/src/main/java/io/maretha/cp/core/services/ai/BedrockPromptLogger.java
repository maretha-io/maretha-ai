package io.maretha.cp.core.services.ai;

import org.apache.commons.logging.Log;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.List;

/**
 * Utility class for logging prompts sent to AWS Bedrock AI.
 */
public class BedrockPromptLogger {

    private BedrockPromptLogger() {
        // Utility class, prevent instantiation
    }

    /**
     * Logs the complete prompt structure being sent to the AI model.
     *
     * @param log          the logger instance
     * @param modelId      the AI model ID
     * @param systemPrompt the system prompt text
     * @param userMessages list of user messages
     */
    public static void logPrompt(Log log, String modelId, String systemPrompt, List<Message> userMessages) {
        log.info("===== Sending prompt to AI =====");
        log.info("Model ID: " + modelId);
        log.info("System Prompt: " + systemPrompt);
        logUserMessages(log, userMessages);
        log.info("=================================");
    }

    /**
     * Logs the complete prompt structure being sent to the AI model with
     * schema/tool configuration.
     *
     * @param log          the logger instance
     * @param modelId      the AI model ID
     * @param systemPrompt the system prompt text
     * @param toolName     the name of the tool being used
     * @param toolSchema   the JSON schema for the tool
     * @param userMessages list of user messages
     */
    public static void logPromptWithSchema(Log log, String modelId, String systemPrompt, String toolName,
            String toolSchema, List<Message> userMessages) {
        log.info("===== Sending prompt to AI (with schema) =====");
        log.info("Model ID: " + modelId);
        log.info("System Prompt: " + systemPrompt);
        log.info("Tool Name: " + toolName);
        log.info("Tool Schema: " + toolSchema);
        logUserMessages(log, userMessages);
        log.info("==============================================");
    }

    /**
     * Logs user messages and their content blocks.
     *
     * @param log          the logger instance
     * @param userMessages list of user messages
     */
    private static void logUserMessages(Log log, List<Message> userMessages) {
        log.info("User Messages Count: " + userMessages.size());
        for (int i = 0; i < userMessages.size(); i++) {
            Message msg = userMessages.get(i);
            log.info("User Message [" + i + "] - Role: " + msg.role());
            logContentBlocks(log, msg.content());
        }
    }

    /**
     * Logs content blocks (text or image).
     *
     * @param log           the logger instance
     * @param contentBlocks list of content blocks
     */
    private static void logContentBlocks(Log log, List<ContentBlock> contentBlocks) {
        for (int j = 0; j < contentBlocks.size(); j++) {
            ContentBlock block = contentBlocks.get(j);
            if (block.text() != null) {
                log.info("  Content Block [" + j + "] - Text: " + block.text());
            } else if (block.image() != null) {
                log.info("  Content Block [" + j + "] - Image (format: " + block.image().format() + ", size: "
                        + block.image().source().bytes().asByteArray().length + " bytes)");
            }
        }
    }
}
