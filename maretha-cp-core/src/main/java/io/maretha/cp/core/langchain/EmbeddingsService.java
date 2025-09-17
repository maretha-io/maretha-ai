package io.maretha.cp.core.langchain;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.common.xcontent.XContentType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EmbeddingsService {

    public void textEmbeddings(DocumentModel doc, Blob blob) {
        // 1. Create a PDF parser
        ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

        // Parse directly from InputStream
        Document document = null;
        try {
            document = pdfParser.parse(blob.getStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var splitter = DocumentSplitters.recursive(1000, 100); // ~1000 chars, 100 overlap
        List<TextSegment> segments = splitter.split(document);
        BulkRequest bulk = new BulkRequest();
        EmbeddingModel embeddingModel = null;

        for (TextSegment segment : segments) {
            Response<Embedding> embedded = embeddingModel.embed(segment);
            Map<String, Object> jsonMap = Map.of(
                    "doc_id", doc.getId(),
                    "title", doc.getTitle(),
                    "content", segment.text(),
                    "text_embeddings", embedded.content().vector() // must match your index mapping dimension
            );

            IndexRequest req = new IndexRequest("embeddings")
//                    .id("123")
                    .source(jsonMap, XContentType.JSON);
            bulk.add(req);
        }

        ElasticSearchAdmin esService = Framework.getService(ElasticSearchAdmin.class);

        BulkResponse response = esService.getClient().bulk(bulk);

        if (response.hasFailures()) {
            System.err.println("Bulk had failures: " + response.buildFailureMessage());
        } else {
            System.out.println("Bulk indexing succeeded: " + response.getItems().length + " docs");
        }
    }

    public void imageEmbeddings(DocumentModel doc, Blob blob) {
        BulkRequest bulk = new BulkRequest();
        float[] embeddings = null; //TODO
        try {
            byte[] byteArray = blob.getByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> jsonMap = Map.of(
                "doc_id", doc.getId(),
                "title", doc.getTitle(),
                "content", blob.getDigest(),
                "image_embeddings", embeddings // must match your index mapping dimension
        );

        IndexRequest req = new IndexRequest("embeddings")
//                    .id("123")
                .source(jsonMap, XContentType.JSON);
        bulk.add(req);
        ElasticSearchAdmin esService = Framework.getService(ElasticSearchAdmin.class);
        BulkResponse response = esService.getClient().bulk(bulk);
    }

    public void embeddings() throws InterruptedException {
//        OpenSearchClient openSearchClient = Framework.getService(OpenSearchClient.class);

        EmbeddingStore<TextSegment> embeddingStore = null;
//                OpenSearchEmbeddingStore.builder()
//                .serverUrl("opensearch.getHttpHostAddress()") //TODO
//                .indexName("embeddings")
//                .openSearchClient(openSearchClient.)
//                .build();

        EmbeddingModel embeddingModel = null;

        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        embeddingStore.add(embedding1, segment1);

        TextSegment segment2 = TextSegment.from("The weather is good today.");
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        embeddingStore.add(embedding2, segment2);

        Thread.sleep(1000); // to be sure that embeddings were persisted

        Embedding queryEmbedding = embeddingModel.embed("What is your favourite sport?").content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(embeddingSearchRequest).matches();
        EmbeddingMatch<TextSegment> embeddingMatch = matches.get(0);

        System.out.println(embeddingMatch.score()); // 0.8144289
        System.out.println(embeddingMatch.embedded().text()); // I like football.
    }

    public void embedPdf(byte[] pdfBytes, EmbeddingStore<TextSegment> embeddingStore) {
        // 1. Create a PDF parser
        ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

        // Parse directly from InputStream
        Document document = pdfParser.parse(new ByteArrayInputStream(pdfBytes));

        var splitter = DocumentSplitters.recursive(1000, 100); // ~1000 chars, 100 overlap
        List<TextSegment> segments = splitter.split(document);

        System.out.println("Segments: " + segments.size());
        System.out.println("First segment: " + segments.get(0).text());

        // 3. Create embedding model (OpenAI example)
        EmbeddingModel embeddingModel = null;
        // 4. In-memory vector store
//        EmbeddingStore<TextSegment> store = new EmbeddingStoreInMemory<>();

        // 5. Generate embeddings and store
        for (TextSegment segment : segments) {
            embeddingStore.add(segment.text(), embeddingModel.embed(segment.text()).content());
        }

    }

}
