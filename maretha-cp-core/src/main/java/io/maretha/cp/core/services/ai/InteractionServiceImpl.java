package io.maretha.cp.core.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maretha.cp.core.models.Interaction;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.Tika;
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
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.maretha.cp.core.services.RunInteractionServiceImpl.getBlob;
import static io.maretha.cp.core.services.RunInteractionServiceImpl.isImage;

public class InteractionServiceImpl implements InteractionService {

    private final BedrockRuntimeClient client = BedrockRuntimeClient.builder()
            .credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    Framework.getProperty("io.maretha.aws.accessKeyId"),
                                    Framework.getProperty("io.maretha.aws.secretAccessKey"))
                    ))
            .region(Region.of(Framework.getProperty("io.maretha.aws.region")))
            .build();

    private final Log log = LogFactory.getLog(InteractionServiceImpl.class);

    private static Document toDocument(JsonNode node) {
        if (node.isObject()) {
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

    public String invokeModel(DocumentModel doc, Interaction interaction, Map<String, Object> paramsData, String resultSchema) {
        var modelId = Framework.getProperty("io.maretha.aws.inferenceProfile") + interaction.getModel();
        var prompt = interaction.getPrompt();
        prompt = """
                You must call the "metadata_extractor" tool to return your answer.
                Extract metadata from the document and populate the tool input according to the schema.
                Do not answer in plain text — only call the tool.
                                
                Populate ALL required fields in the tool's input JSON using the document provided.
                If a value is unknown, return null for that field, not an empty object.
                """;


        ObjectMapper mapper = new ObjectMapper();
        JsonNode resultSchmeaJsonNode = null;
        try {
            resultSchmeaJsonNode = mapper.readTree(resultSchema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ToolInputSchema inputSchema = ToolInputSchema.builder()
                .json(toDocument(resultSchmeaJsonNode))   // pass schema as String
                .build();

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("metadata_extractor")
                .description("Extract Nuxeo metadata in structured JSON.")
                .inputSchema(inputSchema)
                .build();

        ToolConfiguration toolConfig = ToolConfiguration.builder()
                .tools(List.of(Tool.builder().toolSpec(toolSpec).build()))
                .build();

        Blob blob = getBlob(doc);
        Message userMsg;

        if (isImage(blob)) {
            try {
                SdkBytes imageBytes = SdkBytes.fromByteArray(blob.getByteArray());
                ImageBlock imageBlock = ImageBlock.builder()
                        .format("jpeg")               // "jpeg" or "png"
                        .source(s -> s.bytes(imageBytes))
                        .build();
                ContentBlock contentBlock = ContentBlock.fromImage(imageBlock);

                userMsg = Message.builder()
                        .role("user")
                        .content(contentBlock)
                        .build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try (InputStream in = blob.getStream()) {
                Tika tika = new Tika();
                String docText = tika.parseToString(in);
                userMsg = Message.builder()
                        .role("user")
                        .content(ContentBlock.fromText(
                                "Here is the document text:\n\n" + docText
                        ))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Message system = Message.builder()
                .role("user")
                .content(List.of(ContentBlock.fromText(prompt)))
                .build();
        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .inferenceConfig(InferenceConfiguration.builder().build())
                .messages(List.of(
                        system,
                        userMsg
                ))
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
}
