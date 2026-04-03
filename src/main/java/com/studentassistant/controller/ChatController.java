package com.studentassistant.controller;

import com.studentassistant.model.ChatRequest;
import com.studentassistant.model.ChatResponse;
import com.studentassistant.service.ChatService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin("*")
@Slf4j
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getSessionId() == null
                || request.getSessionId().isBlank()
                || request.getMessage() == null
                || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("error", "sessionId and message are required", System.currentTimeMillis()));
        }

        try {
            String answer = chatService.chat(request.getSessionId(), request.getMessage());
            return ResponseEntity.ok(new ChatResponse(request.getSessionId(), answer, System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("Chat request failed", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("error", "Something went wrong: " + e.getMessage(), System.currentTimeMillis()));
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<String> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return ResponseEntity.ok("Session " + sessionId + " cleared.");
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<String>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }
}
