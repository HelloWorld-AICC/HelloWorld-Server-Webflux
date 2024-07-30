package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.domain.Room;
import Helloworld.helloworld_webflux.domain.TranslateLog;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;

public interface ChatService {
    Mono<ChatMessageDTO> saveMessage(ChatMessageDTO message);
    Flux<ChatMessage> getRecentMessages(String roomId);
    Mono<String> translateToKorean(String text);
    Mono<String> translateFromKorean(String text, String targetLanguage);
    Mono<String> getChatbotResponse(String prompt);
    Mono<TranslateLog> saveTranslatedMessage(String roomId, String sender, String content);
    Flux<TranslateLog> getRecentTranslatedMessages(String roomId);
    Mono<String> createPrompt(String koreanQuestion, List<TranslateLog> recentMessages);
    Mono<Room> createOrUpdateRoom(Long userId, String roomId, String message);
    Mono<Tuple2<String, Flux<ChatLogDTO>>> findRecentRoomAndLogs(Long userId);



}
