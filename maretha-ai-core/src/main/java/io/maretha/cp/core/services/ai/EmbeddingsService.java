package io.maretha.cp.core.services.ai;

import org.nuxeo.ecm.core.api.Blob;

import java.util.Map;

public interface EmbeddingsService {

    double[] embedImage(String base64Image, String optionalCaption, Integer outputEmbeddingLength);

    Map<String, double[]> embedPdf(Blob blob);

    double[] embedPdfSummary(Blob blob);

    double[] textToEmbedding(String inputText, Integer outputEmbeddingLength);
}
