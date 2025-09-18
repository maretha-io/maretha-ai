package io.maretha.cp.core.langchain;

import com.amazonaws.SdkClientException;
import org.json.JSONObject;
import org.json.JSONPointer;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class InteractionService {
    public static String invokeModel() {

        // Create a Bedrock Runtime client in the AWS Region you want to use.
        // Replace the DefaultCredentialsProvider with your preferred credentials provider.
        var client = BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();

        // Set the model ID, e.g., Command Light.
        var modelId = "cohere.command-light-text-v14";

        // Define the prompt for the model.
        var prompt = "Describe the purpose of a 'hello world' program in one line.";
        var responseSchema = """
                {
                "type": "object",
                "properties": {
                  "explanation": { "type": "string" }
                },
                "required": ["explanation"]
                }
                """;

        // Build JSON request payload
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
                .replace("{{responseSchema}}", responseSchema);

        try {
            // Encode and send the request to the Bedrock Runtime.
            var response = client.invokeModel(request -> request
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .modelId(modelId)
            );

            // Decode the response body.
            var responseBody = new JSONObject(response.body().asUtf8String());

            // Retrieve the generated text from the model's response.
            var text = new JSONPointer("/generations/0/text").queryFrom(responseBody).toString();
            System.out.println(text);

            return text;

        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", modelId, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
