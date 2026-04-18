package com.studentassistant.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatService {

    private final RagService ragService;
    private final Map<String, List<String>> conversationHistory = new ConcurrentHashMap<>();

    public ChatService(RagService ragService) {
        this.ragService = ragService;
    }

    public String chat(String sessionId, String userMessage) {
        List<String> history = new ArrayList<>(getHistory(sessionId));
        conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(userMessage);

        String answer = ragService.getAnswer(userMessage, history);

        conversationHistory.get(sessionId).add("Assistant: " + answer);

        log.info("Chat response generated for session: {}", sessionId);
        return answer;
    }

    public void clearSession(String sessionId) {
        conversationHistory.remove(sessionId);
        log.info("Cleared session: {}", sessionId);
    }

    public List<String> getHistory(String sessionId) {
        return conversationHistory.getOrDefault(sessionId, new ArrayList<>());
    }
}
