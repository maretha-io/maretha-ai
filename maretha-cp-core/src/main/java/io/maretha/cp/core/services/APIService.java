package io.maretha.cp.core.services;

import io.maretha.cp.core.models.Interaction;
import org.nuxeo.ecm.core.api.Blob;

import java.util.List;
import java.util.Map;

public interface APIService {
    List<Interaction> getInteractions();

    String getInteractionResultAsStream(String interactionId, Map<String, Object> data);

    String getBearerToken();

    String uploadFileToStore(Blob file);

    String uploadS3FileToStore(Blob file, String s3Path);

    double[] textToEmbeddings(String text);

    double[] getDocumentEmbeddings(String docId, String type);

    Map<String, Double> search(String vectorQuery, String type, int limit);
}
