package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.converter.ChatMessageConverter;
import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.domain.Room;
import Helloworld.helloworld_webflux.domain.TranslateLog;
import Helloworld.helloworld_webflux.repository.ChatMessageRepository;
import Helloworld.helloworld_webflux.repository.RoomRepository;
import Helloworld.helloworld_webflux.repository.TranslateLogRepository;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import Helloworld.helloworld_webflux.web.dto.GPTRequest;
import Helloworld.helloworld_webflux.web.dto.GPTResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.core.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    public Mono<String> getChatbotResponse(JsonNode prompt) {
        // WebClient를 사용하여 JSON 요청을 보냅니다.
        Mono<String> web= webClient.post()
                .uri("http://20.214.68.77:5000/question") // Flask 서버 URI
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(prompt)  // JSON 형식으로 요청 본문 설정
                .retrieve()  // 응답을 검색
                .bodyToMono(String.class);  // 응답을 String으로 변환
        System.out.println("fuck");
        return web;
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
    public Mono<JsonNode> createPrompt(String koreanQuestion, List<TranslateLog> recentMessages) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode conversationArray = mapper.createArrayNode();

        // 기존 대화 변환
        for (TranslateLog message : recentMessages) {
            ObjectNode messageNode = mapper.createObjectNode();
            String speaker = message.getSender().equalsIgnoreCase("user") ? "human" : "system";
            messageNode.put("speaker", speaker);
            messageNode.put("utterance", message.getContent());
            conversationArray.add(messageNode);
        }

        // 현재 질문 추가
        ObjectNode currentQuestionNode = mapper.createObjectNode();
        currentQuestionNode.put("speaker", "human");
        currentQuestionNode.put("utterance", koreanQuestion);
        conversationArray.add(currentQuestionNode);

        // 최종 JSON 구조에 추가
        root.set("Conversation", conversationArray);

        System.out.println(root.toString());
        return Mono.just(root);
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

    @Override
    public Mono<Tuple2<String, List<ChatLogDTO>>> findRecentRoomAndLogs(Long userId) {
        return roomRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .flatMap(room -> chatMessageRepository.findByRoomIdOrderByTimeAsc(room.getId())
                        .collectList()
                        .map(messages -> Tuples.of(room.getId(), messages.stream()
                                .map(this::toChatLogDTO)
                                .collect(Collectors.toList()))
                        ));
    }
    private ChatLogDTO toChatLogDTO(ChatMessage message) {
        return new ChatLogDTO(message.getSender(), message.getContent());
    }
}
