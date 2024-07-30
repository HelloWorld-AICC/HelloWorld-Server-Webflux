package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatService {
    Mono<ChatMessageDTO> saveMessage(ChatMessageDTO message);
    Flux<ChatMessage> getRecentMessages(String roomId);
    Mono<String> translateToKorean(String text);
    Mono<String> translateFromKorean(String text, String targetLanguage);
    Mono<String> getChatbotResponse(String prompt);
}
