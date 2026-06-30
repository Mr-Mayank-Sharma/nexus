package com.nexus.oms.controller.ai;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.service.ai.LlmChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final LlmChatService chatService;

    public AiChatController(LlmChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(@RequestBody ChatRequest request) {
        String content = chatService.chat(request.systemPrompt(), request.messages());
        return ResponseEntity.ok(ApiResponse.success(Map.of("content", content)));
    }

    public record ChatRequest(String systemPrompt, List<Map<String, String>> messages) {}
}
