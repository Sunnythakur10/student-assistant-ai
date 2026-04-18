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
            Use the context below (retrieved from study materials) to answer
            the student's question accurately.
            If the context does not contain enough information, say so honestly.

            Conversation History:
            {history}

            Retrieved Context Quality: {relevanceSummary}

            Context:
            {context}

            Student Question: {question}

            Your Answer:
            """;

    private final VectorStore vectorStore;
    private final FuzzyRelevanceScorer fuzzyScorer;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, FuzzyRelevanceScorer fuzzyScorer,
            ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.fuzzyScorer = fuzzyScorer;
        this.chatClient = chatClientBuilder.build();
    }

    public String getAnswer(String question, List<String> history) {
        SearchRequest searchRequest = SearchRequest.builder().query(question).topK(8).build();
        List<Document> results = Optional.ofNullable(vectorStore.similaritySearch(searchRequest))
                .orElse(List.of());

        String context;
        String relevanceSummary;
        if (results.isEmpty()) {
            context = "No sufficiently relevant documents found for this question.";
            relevanceSummary = "Context quality — High: 0, Medium: 0, Low: 0";
        } else {
            List<Double> similarityScores = results.stream()
                    .map(doc -> {
                        String distanceStr = doc.getMetadata().getOrDefault("distance", "0.5").toString();
                        try {
                            return 1.0 - Double.parseDouble(distanceStr);
                        } catch (NumberFormatException ex) {
                            return 0.5;
                        }
                    })
                    .toList();

            List<FuzzyRelevanceScorer.ScoredDocument> scoredDocs =
                    fuzzyScorer.scoreAndFilter(results, similarityScores);

            if (scoredDocs.isEmpty()) {
                context = "No sufficiently relevant documents found for this question.";
                relevanceSummary = "Context quality — High: 0, Medium: 0, Low: 0";
            } else {
                context = scoredDocs.stream()
                        .map(FuzzyRelevanceScorer.ScoredDocument::getDocument)
                        .map(Document::getText)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n\n---\n\n"));
                relevanceSummary = fuzzyScorer.getRelevanceSummary(scoredDocs);
            }
        }

        log.info("Retrieved {} relevant chunks for question", results.size());

        String historyText = (history == null || history.isEmpty())
                ? "No previous conversation."
                : String.join("\n", history);

        Map<String, Object> model = Map.of(
                "context", context,
                "question", question,
                "history", historyText,
                "relevanceSummary", relevanceSummary);
        Prompt prompt = new PromptTemplate(RAG_PROMPT_TEMPLATE).create(model);

        String answer = Optional.ofNullable(chatClient.prompt(prompt).call().content()).orElse("");

        log.info("Generated answer for question");
        return answer;
    }
}
