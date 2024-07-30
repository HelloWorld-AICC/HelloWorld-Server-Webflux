package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.service.ChatService;
import Helloworld.helloworld_webflux.service.UserService;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

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
                                                                    chatService.saveTranslatedMessage(updatedRoomId, "user", koreanQuestion).subscribe();
                                                                    chatService.saveTranslatedMessage(updatedRoomId, "bot", response).subscribe();
                                                                    ChatMessageDTO userMessage = new ChatMessageDTO(null, updatedRoomId, "user", question, LocalDateTime.now());
                                                                    ChatMessageDTO botMessage = new ChatMessageDTO(null, updatedRoomId, "bot", userResponse, LocalDateTime.now());
                                                                    chatService.saveMessage(userMessage).subscribe();
                                                                    chatService.saveMessage(botMessage).subscribe();

                                                                    return Flux.fromStream(userResponse.chars()
                                                                                    .mapToObj(c -> String.valueOf((char) c)))
                                                                            .delayElements(Duration.ofMillis(50))
                                                                            .concatWith(Flux.just("Room ID: " + updatedRoomId)); // 마지막에 Room ID 반환
                                                                });
                                                            })
                                                    )
                                            );
                                })
                        )
                );
    }

    @GetMapping("/recent-room")
    public Flux<String> getRecentRoomAndLogs(@RequestHeader("user_id") Long userId) {
        return chatService.findRecentRoomAndLogs(userId)
                .flatMapMany(roomAndLogs -> {
                    String roomId = roomAndLogs.getT1();
                    Flux<ChatLogDTO> logs = roomAndLogs.getT2();
                    return Flux.concat(Mono.just("Room ID: " + roomId +"\n"), logs.map(log ->"Sender: " + log.getSender() + " ,Content: " + log.getContent()+"\n"));
                });
    }

}
