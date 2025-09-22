package io.maretha.cp.core.services.ai;

import io.maretha.cp.core.models.Interaction;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

import java.util.List;
import java.util.Map;

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

    public String invokeModel(Interaction interaction, Map<String, Object> paramsData, String resultSchema) {
        var modelId = interaction.getModel();
        var prompt = interaction.getPrompt();


        ToolInputSchema inputSchema = ToolInputSchema.builder()
                .json(Document.fromString(resultSchema))   // pass schema as String
                .build();

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("metadata_extractor")
                .description("Extract Nuxeo metadata in structured JSON.")
                .inputSchema(inputSchema)
                .build();

        ToolConfiguration toolConfig = ToolConfiguration.builder()
                .tools(List.of(Tool.builder().toolSpec(toolSpec).build()))
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .messages(List.of(
                        Message.builder()
                                .role("user")
                                .content(List.of(ContentBlock.fromText(prompt)))
                                .build()
                ))
                .toolConfig(toolConfig)
                .build();

        ConverseResponse response = client.converse(request);

        try {
//            ObjectMapper mapper = new ObjectMapper();
//
//            // Wrap the pieces into a proper object
//            var requestMap = new LinkedHashMap<String, Object>();
//            requestMap.put("message", prompt);
//            var responseFormat = new LinkedHashMap<String, Object>();
//            responseFormat.put("type", "json");
//            responseFormat.put("json_schema", mapper.readTree(resultSchema));
//            requestMap.put("response_format", responseFormat);
//
//            // Convert to compact or pretty JSON
//            String requestBodyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestMap);
//
//            // Encode and send the request to the Bedrock Runtime.
//            var response = client.invokeModel(request -> request
//                    .body(SdkBytes.fromUtf8String(requestBodyJson))
//                    .modelId(modelId)
//            );

            var toolUse = response.output()
                    .message()
                    .content()
                    .getFirst()
                    .toolUse();

            var responseBody = new JSONObject(toolUse.input());

            // Decode the response body.
//            var responseBody = new JSONObject(response.output().message().content().getFirst().text());

            // Retrieve the generated text from the model's response.
            return new JSONPointer("/output/message/content/0/toolUse/input").queryFrom(responseBody).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
