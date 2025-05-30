package io.maretha.cp.core.services;

import io.maretha.cp.core.models.Interaction;
import org.nuxeo.ecm.core.api.Blob;

import java.util.List;
import java.util.Map;

public interface APIService {
    List<Interaction> getInteractions();

    String getInteractionResult(String interactionId, Map<String, Object> data, boolean stream);

    String getInteractionResultWithResultSchema(String interaction, Map<String, Object> data, String resultSchema, boolean stream);

    String getBearerToken();

    String uploadFileToStore(Blob file);

    String uploadS3FileToStore(Blob file, String s3Path);

    double[] textToEmbeddings(String text);

    double[] getDocumentEmbeddings(String docId, String type);

    Map<String, Double> search(String vectorQuery, String type, int limit);

    Map<String, Double> searchSimilarity(String storeId, String type, int limit);

    String checkProcessingStatusCompleted(String storeId, boolean withRetry);
}
