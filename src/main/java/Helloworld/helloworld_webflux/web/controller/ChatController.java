package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.service.ChatService;
import Helloworld.helloworld_webflux.service.UserService;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {
    private final ChatService chatService;
    private final UserService userService;

    @GetMapping("/language")
    public Mono<String> getLanguage(@RequestHeader("user_id") Long userId) {
        return userService.findLanguage(userId);
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestHeader("user_id") Long userId,
                                    @RequestParam("roomId") String roomId,
                                    @RequestBody String question) {
        return userService.findLanguage(userId)
                .flatMapMany(language -> chatService.translateToKorean(question)
                        .flatMapMany(koreanQuestion -> chatService.getRecentMessages(roomId)
                                .collectList()
                                .flatMapMany(recentMessages -> {
                                    String prompt = createPrompt(koreanQuestion, recentMessages);
                                    return chatService.getChatbotResponse(prompt)
                                            .flatMapMany(response -> chatService.translateFromKorean(response, language));
                                })
                                .doOnNext(response -> {
                                    ChatMessageDTO userMessage = new ChatMessageDTO(null, roomId, "user", question, LocalDateTime.now());
                                    ChatMessageDTO botMessage = new ChatMessageDTO(null, roomId, "bot", response, LocalDateTime.now());
                                    chatService.saveMessage(userMessage).subscribe();
                                    chatService.saveMessage(botMessage).subscribe();
                                })
                        )
                );
    }

    private String createPrompt(String koreanQuestion, List<ChatMessage> recentMessages) {
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : recentMessages) {
            prompt.append(message.getSender()).append(": ").append(message.getContent()).append("\n");
        }
        prompt.append("User: ").append(koreanQuestion);
        return prompt.toString();
    }
}