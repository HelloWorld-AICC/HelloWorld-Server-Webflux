package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.domain.TranslateLog;
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
                        .flatMapMany(koreanQuestion -> chatService.getRecentTranslatedMessages(roomId)
                                .collectList()
                                .flatMapMany(recentMessages -> {
                                    String prompt = createPrompt(koreanQuestion, recentMessages);
                                    return chatService.getChatbotResponse(prompt)
                                            .flatMapMany(response -> {
                                                Mono<String> translatedResponse = chatService.translateFromKorean(response, language);
                                                // 한국어 번역된 대화 로그와 사용자 언어의 대화 로그 모두 저장
                                                return translatedResponse.flatMapMany(userResponse -> {
                                                    chatService.saveTranslatedMessage(roomId, "user", koreanQuestion).subscribe();
                                                    chatService.saveTranslatedMessage(roomId, "bot", response).subscribe();
                                                    ChatMessageDTO userMessage = new ChatMessageDTO(null, roomId, "user", question, LocalDateTime.now());
                                                    ChatMessageDTO botMessage = new ChatMessageDTO(null, roomId, "bot", userResponse, LocalDateTime.now());
                                                    chatService.saveMessage(userMessage).subscribe();
                                                    chatService.saveMessage(botMessage).subscribe();
                                                    return Flux.just(userResponse);
                                                });
                                            });
                                })
                        )
                );
    }

    private String createPrompt(String koreanQuestion, List<TranslateLog> recentMessages) {
        StringBuilder prompt = new StringBuilder();
        for (TranslateLog message : recentMessages) {
            prompt.append(message.getSender()).append(": ").append(message.getContent()).append("\n");
        }
        prompt.append("User: ").append(koreanQuestion);
        return prompt.toString();
    }
}
