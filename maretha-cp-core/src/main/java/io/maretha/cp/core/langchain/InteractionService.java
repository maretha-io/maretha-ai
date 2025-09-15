package io.maretha.cp.core.langchain;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

public class InteractionService {
    public String promptToText() {
        PromptTemplate promptTemplate = PromptTemplate.from("Say '{{text}}' in {{language}}.");

        Map<String, Object> variables = new HashMap<>();
        variables.put("text", "hi");
        variables.put("language", "German");

        Prompt prompt = promptTemplate.apply(variables);

        String text = prompt.text();
        System.out.println(text); // Say 'hi' in German.
        return text;
    }

    interface Assistant {
        TokenStream chat(String message);
    }

    public String interactWithAi() throws ExecutionException, InterruptedException, TimeoutException {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
//                .apiKey(ApiKeys.OPENAI_API_KEY)//TODO
                .modelName(GPT_4_O_MINI)
                .build();

        Assistant assistant = AiServices.create(Assistant.class, model);

        TokenStream tokenStream = assistant.chat("Tell me a joke");

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        ChatResponse chatResponse = futureResponse.get(30, TimeUnit.SECONDS);
        System.out.println("\n" + chatResponse);
        return chatResponse.aiMessage().text();
    }
}
