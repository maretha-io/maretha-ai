package io.maretha.cp.core.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmbeddingsServiceImpl implements EmbeddingsService {

    private static final String AMAZON_TITAN_EMBED_TEXT_V_2_0 = "amazon.titan-embed-text-v2:0";
    private static final String AMAZON_TITAN_EMBED_IMAGE_V_1 = "amazon.titan-embed-image-v1";
    private final BedrockRuntimeClient client = BedrockRuntimeClient.builder()
            .credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    Framework.getProperty("io.maretha.aws.accessKeyId"),
                                    Framework.getProperty("io.maretha.aws.secretAccessKey"))
                    ))
            .region(Region.of(Framework.getProperty("io.maretha.aws.region")))
            .build();
    private final Log log = LogFactory.getLog(EmbeddingsServiceImpl.class);

    @Override
    public double[] embedImage(String base64Image, String optionalCaption, Integer outputEmbeddingLength) {
        // Build native request JSON
        JSONObject req = new JSONObject();
        // inputImage is base64-encoded image string
        req.put("inputImage", base64Image);
        if (optionalCaption != null && !optionalCaption.isBlank()) {
            req.put("inputText", optionalCaption);
        }
        if (outputEmbeddingLength != null) {
            JSONObject embeddingConfig = new JSONObject();
            embeddingConfig.put("outputEmbeddingLength", outputEmbeddingLength);
            req.put("embeddingConfig", embeddingConfig);
        }

        String nativeRequest = req.toString();

        var response = client.invokeModel(r -> r
                .modelId(AMAZON_TITAN_EMBED_IMAGE_V_1)
                .body(SdkBytes.fromUtf8String(nativeRequest))
        );
        String bodyStr = response.body().asUtf8String();
        JSONObject respJson = new JSONObject(bodyStr);
        JSONArray arr = respJson.getJSONArray("embedding");

        double[] embedding = new double[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            embedding[i] = arr.getDouble(i);
        }
        return embedding;

    }

    @Override
    public Map<String, double[]> embedPdf(Blob blob) {
        ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
        Document document;
        try {
            document = pdfParser.parse(blob.getStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, double[]> textEmbeddingsMap = new HashMap<>();

        var splitter = DocumentSplitters.recursive(1000, 100); // ~1000 chars, 100 overlap
        List<TextSegment> segments = splitter.split(document);

        for (TextSegment segment : segments) {
            double[] embeddings = textToEmbedding(segment.text());
            textEmbeddingsMap.put(segment.text(), embeddings);
        }

        return textEmbeddingsMap;
    }

    @Override
    public double[] embedPdfSummary(Blob blob) {
        ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
        Document document;
        try {
            document = pdfParser.parse(blob.getStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String text = document.text();
        return textToEmbedding(text);
    }

    @Override
    public double[] textToEmbedding(String inputText) {
        // Set the model ID, e.g., Titan Text Embeddings V2.
        ObjectMapper mapper = new ObjectMapper();

        try {
            // Optional: normalize whitespace so it's cleaner
            String safeText = inputText.replaceAll("\\s+", " ").trim();

            // Build payload as a Map -> Jackson makes valid JSON
            Map<String, Object> payload = new HashMap<>();
            payload.put("inputText", safeText);

            // Convert to JSON
            String json = mapper.writeValueAsString(payload);
            // Encode and send the request to the Bedrock Runtime.
            var response = client.invokeModel(request -> request
                    .body(SdkBytes.fromUtf8String(json))
                    .modelId(AMAZON_TITAN_EMBED_TEXT_V_2_0)
            );

            // Decode the response body.
            JSONObject responseBody = new JSONObject(response.body().asUtf8String());
            JSONArray arr = responseBody.getJSONArray("embedding");

            double[] embedding = new double[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                embedding[i] = arr.getDouble(i);
            }
            return embedding;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
