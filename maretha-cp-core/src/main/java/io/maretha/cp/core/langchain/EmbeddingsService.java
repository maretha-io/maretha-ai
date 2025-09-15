package io.maretha.cp.core.langchain;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.ByteArrayInputStream;
import java.util.List;

public class EmbeddingsService {

    public void embeddings() throws InterruptedException {
        EmbeddingStore<TextSegment> embeddingStore = null;
//                OpenSearchEmbeddingStore.builder()
//                .serverUrl("opensearch.getHttpHostAddress()") //TODO
//                .indexName("embeddings")
////                .openSearchClient("")
//                .build();

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

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
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-small") // or "text-embedding-3-large"
                .build();

        // 4. In-memory vector store
//        EmbeddingStore<TextSegment> store = new EmbeddingStoreInMemory<>();

        // 5. Generate embeddings and store
        for (TextSegment segment : segments) {
            embeddingStore.add(segment.text(), embeddingModel.embed(segment.text()).content());
        }

    }
}
