package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.converter.ChatMessageConverter;
import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.repository.ChatMessageRepository;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import Helloworld.helloworld_webflux.web.dto.GPTRequest;
import Helloworld.helloworld_webflux.web.dto.GPTResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatMessageRepository chatMessageRepository;
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
}
