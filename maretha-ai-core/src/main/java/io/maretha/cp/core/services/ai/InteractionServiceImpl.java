package io.maretha.cp.core.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import io.maretha.cp.core.models.Interaction;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.services.RunInteractionServiceImpl.getBlob;
import static io.maretha.cp.core.services.RunInteractionServiceImpl.isImage;

public class InteractionServiceImpl implements InteractionService {

    private static final String METADATA_EXTRACTOR_TOOL_PROMPT = "" +
            "Use the tool \"metadata_extractor\" to return your answer.\n" +
            "Extract metadata from the document and populate the tool input according to the schema.\n" +
            "\n" +
            "Populate ALL required fields in the tool's input JSON using the document provided.\n" +
            "If a value is unknown, do not return it.";

    private final BedrockRuntimeClient client = BedrockRuntimeClient.builder()
            .credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    Framework.getProperty("io.maretha.aws.accessKeyId"),
                                    Framework.getProperty("io.maretha.aws.secretAccessKey"))))
            .region(Region.of(Framework.getProperty("io.maretha.aws.region")))
            .build();

    private static final Log log = LogFactory.getLog(InteractionServiceImpl.class);

    private static Document toDocument(JsonNode node) {
        if (node.isObject())

        {
            var mb = Document.mapBuilder();
            node.fields().forEachRemaining(e -> mb.putDocument(e.getKey(), toDocument(e.getValue())));
            return mb.build();
        } else if (node.isArray()) {
            var lb = Document.listBuilder();
            node.forEach(child -> lb.addDocument(toDocument(child)));
            return lb.build();
        } else if (node.isTextual()) {
            return Document.fromString(node.textValue());
        } else if (node.isNumber()) {
            return Document.fromNumber(node.decimalValue());
        } else if (node.isBoolean()) {
            return Document.fromBoolean(node.booleanValue());
        } else if (node.isNull()) {
            return Document.fromNull();
        } else {
            return Document.fromString(node.asText());
        }
    }

    @Override
    public String invokeModel(DocumentModel doc, Interaction interaction, Map<String, Object> paramsData) {
        var modelId = Framework.getProperty("io.maretha.aws.inferenceProfile") + interaction.getModel();
        var prompt = interaction.getPrompt();

        List<Message> userMessages = new ArrayList<>();
        processParamsAndBlobs(doc, paramsData, userMessages);

        SystemContentBlock systemPrompt = SystemContentBlock.builder()
                .text(prompt)
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .inferenceConfig(InferenceConfiguration.builder().build())
                .system(systemPrompt)
                .messages(userMessages)
                .build();

        ConverseResponse response = client.converse(request);
        StringBuilder sb = new StringBuilder();

        try {
            var contentBlocks = response.output().message().content();
            for (ContentBlock block : contentBlocks) {
                if (block.text() != null) {
                    sb.append(block.text()).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String invokeModelWithSchema(DocumentModel doc, Interaction interaction, Map<String, Object> paramsData,
            String resultSchema) {
        var modelId = Framework.getProperty("io.maretha.aws.inferenceProfile") + interaction.getModel();
        var prompt = interaction.getPrompt();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode resultSchemaJsonNode = null;
        try {
            resultSchemaJsonNode = mapper.readTree(resultSchema);
            log.debug("ResultSchema: " + resultSchema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ToolInputSchema inputSchema = ToolInputSchema.builder()
                .json(toDocument(resultSchemaJsonNode)) // pass schema as String
                .build();

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("metadata_extractor")
                .description("Extract Nuxeo metadata in structured JSON.")
                .inputSchema(inputSchema)
                .build();

        ToolConfiguration toolConfig = ToolConfiguration.builder()
                .tools(List.of(Tool.builder().toolSpec(toolSpec).build()))
                .build();

        List<Message> userMessages = new ArrayList<>();
        processParamsAndBlobs(doc, paramsData, userMessages);

        SystemContentBlock systemPrompt = SystemContentBlock.builder()
                .text(METADATA_EXTRACTOR_TOOL_PROMPT + " Prompt: " + prompt)
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .inferenceConfig(InferenceConfiguration.builder().build())
                .system(systemPrompt)
                .messages(userMessages)
                .toolConfig(toolConfig)
                .build();

        ConverseResponse response = client.converse(request);

        try {
            var contentBlocks = response.output().message().content();
            for (ContentBlock block : contentBlocks) {
                if (block.text() != null) {
                    log.debug("Text: " + block.text());
                }
                if (block.toolUse() != null) {
                    ToolUseBlock toolUse = block.toolUse();
                    log.debug("Tool name: " + toolUse.name());
                    log.debug("Tool input JSON: " + toolUse.input());
                    return toolUse.input().toString();
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void processParamsAndBlobs(DocumentModel doc, Map<String, Object> paramsData, List<Message> userMessages) {
        Blob mainBlob = getBlob(doc);
        // Add main document blob if present
        if (mainBlob != null) {
            addBlobContentToMessages(mainBlob, userMessages);
        }

        // Process paramsData
        if (MapUtils.isNotEmpty(paramsData)) {
            for (Map.Entry<String, Object> entry : paramsData.entrySet()) {
                Object value = entry.getValue();

                if (value instanceof Blob) {
                    addBlobContentToMessages((Blob) value, userMessages);
                    entry.setValue("[Content attached as " + ((Blob) value).getFilename() + "]");
                } else if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    if (!list.isEmpty() && list.get(0) instanceof Blob) {
                        for (Object item : list) {
                            if (item instanceof Blob) {
                                addBlobContentToMessages((Blob) item, userMessages);
                            }
                        }
                        entry.setValue("[Values list of " + list.size() + " Blobs attached]");
                    }
                }
            }

            // After processing Blobs, serialize the paramsData
            try {
                Message paramsUserMessage = Message.builder()
                        .role("user")
                        .content(ContentBlock.fromText(new ObjectMapper().writeValueAsString(paramsData)))
                        .build();
                userMessages.add(paramsUserMessage);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void addBlobContentToMessages(Blob blob, List<Message> userMessages) {
        if (blob == null)
            return;

        if (isImage(blob)) {
            try {
                SdkBytes imageBytes = SdkBytes.fromByteArray(blob.getByteArray());
                ImageBlock imageBlock = ImageBlock.builder()
                        .format("jpeg") // "jpeg" or "png" - Bedrock requires specific formats, assuming jpeg for now or
                                        // extracting extension
                        .source(s -> s.bytes(imageBytes))
                        .build();
                ContentBlock contentBlock = ContentBlock.fromImage(imageBlock);

                Message userMsg = Message.builder()
                        .role("user")
                        .content(contentBlock)
                        .build();
                userMessages.add(userMsg);
            } catch (IOException e) {
                log.error("Failed to read image blob", e);
            }
        } else if ("application/pdf".equalsIgnoreCase(blob.getMimeType())) {
            try (InputStream in = blob.getStream()) {
                ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
                var document = pdfParser.parse(in);
                Message userMsg = Message.builder()
                        .role("user")
                        .content(ContentBlock.fromText(
                                "<DOCUMENT name=\"" + blob.getFilename() + "\">:\n" + document.text()
                                        + " \n</DOCUMENT>"))
                        .build();
                userMessages.add(userMsg);
            } catch (Exception e) {
                log.error("Failed to parse PDF blob", e);
            }
        }
    }
}
