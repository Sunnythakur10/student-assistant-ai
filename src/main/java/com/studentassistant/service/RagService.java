package com.studentassistant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RagService {

    private static final String RAG_PROMPT_TEMPLATE = """
            You are a helpful and friendly student assistant AI.
            Use the context below (retrieved from study materials) to answer the student's question.
            If the context does not contain enough information, say so honestly and give a general answer.

            Context:
            {context}

            Student Question: {question}

            Your Answer:
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public String getAnswer(String question) {
        SearchRequest searchRequest = SearchRequest.builder().query(question).topK(5).build();
        List<Document> results = Optional.ofNullable(vectorStore.similaritySearch(searchRequest))
                .orElse(List.of());

        String context;
        if (results.isEmpty()) {
            context = "No relevant documents found.";
        } else {
            context = results.stream()
                    .map(Document::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n---\n\n"));
        }

        log.info("Retrieved {} relevant chunks for question", results.size());

        Map<String, Object> model = Map.of("context", context, "question", question);
        Prompt prompt = new PromptTemplate(RAG_PROMPT_TEMPLATE).create(model);

        String answer = Optional.ofNullable(chatClient.prompt(prompt).call().content()).orElse("");

        log.info("Generated answer for question");
        return answer;
    }
}
