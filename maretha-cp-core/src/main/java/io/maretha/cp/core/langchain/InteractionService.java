package io.maretha.cp.core.langchain;

import com.amazonaws.SdkClientException;
import io.maretha.cp.core.models.Interaction;
import org.json.JSONObject;
import org.json.JSONPointer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.Map;

public class InteractionService {
    private final BedrockRuntimeClient client = BedrockRuntimeClient.builder()
            .credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("YOUR_ACCESS_KEY", "YOUR_SECRET_KEY")
                    )
            )
            .region(Region.US_EAST_1)
            .build();

    public String invokeModel(Interaction interaction, Map<String, Object> paramsData, String resultSchema) {
        var modelId = interaction.getModel();
        var prompt = interaction.getPrompt();
        String requestBody = """
                {
                  "message": "{{prompt}}",
                  "response_format": {
                    "type": "json",
                    "json_schema": {{responseSchema}}
                  }
                }
                """
                .replace("{{prompt}}", prompt)
                .replace("{{responseSchema}}", resultSchema);

        try {
            // Encode and send the request to the Bedrock Runtime.
            var response = client.invokeModel(request -> request
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .modelId(modelId)
            );

            // Decode the response body.
            var responseBody = new JSONObject(response.body().asUtf8String());

            // Retrieve the generated text from the model's response.
            return new JSONPointer("/generations/0/text").queryFrom(responseBody).toString();

        } catch (SdkClientException e) {
            throw new RuntimeException(e);
        }
    }
}
