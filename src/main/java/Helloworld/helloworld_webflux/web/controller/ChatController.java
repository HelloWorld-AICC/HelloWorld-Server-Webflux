package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.service.ChatService;
import Helloworld.helloworld_webflux.service.UserService;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import Helloworld.helloworld_webflux.web.dto.RecentRoomDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
                        .flatMapMany(koreanQuestion -> chatService.createOrUpdateRoom(userId, roomId, question)
                                .flatMapMany(room -> {
                                    String updatedRoomId = room.getId();
                                    return chatService.getRecentTranslatedMessages(updatedRoomId)
                                            .collectList()
                                            .flatMapMany(recentMessages -> chatService.createPrompt(koreanQuestion, recentMessages)
                                                    .flatMapMany(prompt -> chatService.getChatbotResponse(prompt)
                                                            .flatMapMany(response -> {
                                                                Mono<String> translatedResponse = chatService.translateFromKorean(response, language);
                                                                return translatedResponse.flatMapMany(userResponse -> {
                                                                    return chatService.saveTranslatedMessage(updatedRoomId, "user", koreanQuestion)
                                                                            .then(chatService.saveTranslatedMessage(updatedRoomId, "bot", response))
                                                                            .then(Mono.defer(() -> {
                                                                                ChatMessageDTO userMessage = new ChatMessageDTO(null, updatedRoomId, "user", question, LocalDateTime.now());
                                                                                return chatService.saveMessage(userMessage);
                                                                            }))
                                                                            .then(Mono.defer(() -> {
                                                                                ChatMessageDTO botMessage = new ChatMessageDTO(null, updatedRoomId, "bot", userResponse, LocalDateTime.now());
                                                                                return chatService.saveMessage(botMessage);
                                                                            }))
                                                                            .thenMany(Flux.fromStream(userResponse.chars()
                                                                                            .mapToObj(c -> String.valueOf((char) c)))
                                                                                    .delayElements(Duration.ofMillis(25))
                                                                                    .concatWith(Flux.just("Room ID: " + updatedRoomId)));
                                                                });
                                                            })
                                                    )
                                            );
                                })
                        )
                );
    }


    @GetMapping("/recent-room")
    public Mono<RecentRoomDTO> getRecentRoomAndLogs(@RequestHeader("user_id") Long userId) {
        return chatService.findRecentRoomAndLogs(userId)
                .map(roomAndLogs -> {
                    String roomId = roomAndLogs.getT1();
                    List<ChatLogDTO> logs = roomAndLogs.getT2();
                    return new RecentRoomDTO(roomId, logs);
                });
    }

}
