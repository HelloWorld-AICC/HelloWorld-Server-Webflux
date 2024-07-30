package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.converter.ChatMessageConverter;
import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.domain.Room;
import Helloworld.helloworld_webflux.domain.TranslateLog;
import Helloworld.helloworld_webflux.repository.ChatMessageRepository;
import Helloworld.helloworld_webflux.repository.RoomRepository;
import Helloworld.helloworld_webflux.repository.TranslateLogRepository;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import Helloworld.helloworld_webflux.web.dto.GPTRequest;
import Helloworld.helloworld_webflux.web.dto.GPTResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final TranslateLogRepository translateLogRepository;
    private final RoomRepository roomRepository;

    private final WebClient webClient;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Override
    public Mono<ChatMessageDTO> saveMessage(ChatMessageDTO message) {
        ChatMessage entity = ChatMessageConverter.toEntity(message);
        return chatMessageRepository.save(entity).map(ChatMessageConverter::toDTO);
    }

    @Override
    public Flux<ChatMessage> getRecentMessages(String roomId) {
        return chatMessageRepository.findTop10ByRoomIdOrderByTimeDesc(roomId);
    }

    @Override
    public Mono<String> translateToKorean(String text) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a translator.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user", "Exactly Translate the following text to Korean: " + text);
        GPTRequest request = new GPTRequest("gpt-3.5-turbo", List.of(systemMessage, userMessage), 1000);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

    @Override
    public Mono<String> translateFromKorean(String text, String targetLanguage) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a translator.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user", "Exactly translate the following Korean text to " + targetLanguage + ": " + text);
        GPTRequest request = new GPTRequest("gpt-3.5-turbo", List.of(systemMessage, userMessage), 1000);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

    @Override
    public Mono<String> getChatbotResponse(String prompt) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a helpful assistant.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user", prompt);
        GPTRequest request = new GPTRequest("gpt-3.5-turbo", List.of(systemMessage, userMessage), 1000);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

    @Override
    public Mono<TranslateLog> saveTranslatedMessage(String roomId, String sender, String content) {
        TranslateLog log = new TranslateLog();
        log.setRoomId(roomId);
        log.setSender(sender);
        log.setContent(content);
        log.setTime(LocalDateTime.now());
        return translateLogRepository.save(log);
    }

    @Override
    public Flux<TranslateLog> getRecentTranslatedMessages(String roomId) {
        return translateLogRepository.findTop10ByRoomIdOrderByTimeDesc(roomId);
    }

    @Override
    public Mono<String> createPrompt(String koreanQuestion, List<TranslateLog> recentMessages) {
        StringBuilder prompt = new StringBuilder();
        for (TranslateLog message : recentMessages) {
            prompt.append(message.getSender()).append(": ").append(message.getContent()).append("\n");
        }
        prompt.append("User: ").append(koreanQuestion);
        return Mono.just(prompt.toString());
    }

    @Override
    public Mono<Room> createOrUpdateRoom(Long userId, String roomId, String message) {
        // 만약 roomId가 "new_chat"인 경우 새 방 생성
        if ("new_chat".equals(roomId)) {
            String title = message.length() > 20 ? message.substring(0, 17) + "..." : message;
            Room room = new Room();
            room.setUserId(userId);
            room.setTitle(title);
            room.setUpdatedAt(LocalDateTime.now());
            return roomRepository.save(room);
        } else {
            // 기존 방 업데이트
            return roomRepository.findByUserIdAndId(userId, roomId)
                    .flatMap(existingRoom -> {
                        existingRoom.setUpdatedAt(LocalDateTime.now());
                        return roomRepository.save(existingRoom);
                    });
        }
    }
}
