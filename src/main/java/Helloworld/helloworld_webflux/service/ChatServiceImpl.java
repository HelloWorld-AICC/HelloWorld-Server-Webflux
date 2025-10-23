package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.converter.ChatMessageConverter;
import Helloworld.helloworld_webflux.domain.ChatMessage;
import Helloworld.helloworld_webflux.domain.Room;
import Helloworld.helloworld_webflux.domain.TranslateLog;
import Helloworld.helloworld_webflux.repository.ChatMessageRepository;
import Helloworld.helloworld_webflux.repository.RoomRepository;
import Helloworld.helloworld_webflux.repository.TranslateLogRepository;
import Helloworld.helloworld_webflux.repository.UserRepository;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.ChatMessageDTO;
import Helloworld.helloworld_webflux.web.dto.GPTRequest;
import Helloworld.helloworld_webflux.web.dto.GPTResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final UserService userService;
    private final ChatMessageRepository chatMessageRepository;
    private final TranslateLogRepository translateLogRepository;
    private final RoomRepository roomRepository;

    private final WebClient webClient;
    private final UserRepository userRepository;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Override
    public Flux<String> chatAnswer(String gmail, String roomId, String question) {
        return userService.findLanguage(gmail)
                .flatMapMany(language -> translateToKorean(question)
                        .flatMapMany(koreanQuestion -> createOrUpdateRoom(gmail, roomId, question)
                                .flatMapMany(room -> getRoomAndProcessMessages(room.getId(), question, koreanQuestion, language))
                        )
                );
    }

    private Flux<String> getRoomAndProcessMessages(String updatedRoomId, String question, String koreanQuestion, String language) {
        return getRecentTranslatedMessages(updatedRoomId)
                .collectList()
                .flatMapMany(recentMessages -> createPrompt(koreanQuestion, recentMessages)
                        .flatMapMany(prompt -> getChatbotResponse(prompt)
                                .flatMapMany(response -> processResponse(updatedRoomId, question, koreanQuestion, response, language))
                        )
                );
    }

//    private Flux<String> processResponse(String roomId, String question, String koreanQuestion, String botResponse, String language) {
//        return translateFromKorean(botResponse, language)
//                .flatMapMany(userResponse ->
//                        Flux.just(
//                                        Mono.defer(() -> saveTranslatedMessage(roomId, "user", koreanQuestion)),
//                                        Mono.defer(() -> saveTranslatedMessage(roomId, "bot", botResponse)),
//                                        Mono.defer(() -> saveMessage(new ChatMessageDTO(null, roomId, "user", question, LocalDateTime.now()))),
//                                        Mono.defer(() -> saveMessage(new ChatMessageDTO(null, roomId, "bot", userResponse, LocalDateTime.now())))
//                                ).concatMap(mono -> mono)
//                                .thenMany(Flux.fromStream(userResponse.chars()
//                                                .mapToObj(c -> String.valueOf((char) c)))
//                                        .delayElements(Duration.ofMillis(25))
//                                        .concatWith(Flux.just("Room ID: " + roomId))
//                                )
//                );
//    }
private Flux<String> processResponse(String roomId, String question, String koreanQuestion, String botResponse, String language) {

    Flux.merge(
            saveTranslatedMessage(roomId, "user", koreanQuestion).then(),
            saveTranslatedMessage(roomId, "bot", botResponse).then(),
            saveMessage(new ChatMessageDTO(null, roomId, "user", question, LocalDateTime.now())).then()
    ).subscribe();

    return translateFromKorean(botResponse, language)
            .doOnComplete(() -> {
                translateFromKorean(botResponse, language)
                        .collectList()
                        .map(list -> String.join("", list))
                        .flatMap(fullTranslated ->
                                saveMessage(new ChatMessageDTO(null, roomId, "bot", fullTranslated, LocalDateTime.now()))
                        ).subscribe();
            })
            .concatWith(Flux.just("\nRoom ID: " + roomId));
}





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
        GPTRequest.Message userMessage = new GPTRequest.Message("user",
                "Translate the following text into Korean accurately and naturally. " +
                        "Respond with only the translated Korean sentence. Do not include any explanations, labels, or formatting." +
                        "Respond with **plain text only**:\n\n" +
                        "Text: " + text);

        GPTRequest request = new GPTRequest("gpt-4o-mini", List.of(systemMessage, userMessage), 1000);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> response.getChoices().get(0).getMessage().getContent());
    }

//    @Override
//    public Mono<String> translateFromKorean(String text, String targetLanguage) {
//        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a translator.");
//        GPTRequest.Message userMessage = new GPTRequest.Message("user",
//                "Translate the following Korean text into " + targetLanguage + " accurately and naturally. " +
//                        "Respond with only the translated sentence in " + targetLanguage + ". Do not include any other text, explanation, or labels." +
//                        "Respond with **plain text only**:\n\n" +
//                        "Text: " + text);
//        GPTRequest request = new GPTRequest("gpt-4o-mini", List.of(systemMessage, userMessage), 1000);
//
//        return webClient.post()
//                .uri("https://api.openai.com/v1/chat/completions")
//                .header("Authorization", "Bearer " + openaiApiKey)
//                .header("Content-Type", "application/json")
//                .bodyValue(request)
//                .retrieve()
//                .bodyToMono(GPTResponse.class)
//                .map(response -> response.getChoices().get(0).getMessage().getContent());
//    }
@Override
public Flux<String> translateFromKorean(String text, String targetLanguage) {
    GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a translator.");
    GPTRequest.Message userMessage = new GPTRequest.Message("user",
            "Translate the following Korean text into " + targetLanguage + " accurately and naturally. " +
                    "Respond with only the translated sentence in " + targetLanguage + ". Do not include any other text, explanation, or labels." +
                    "Respond with plain text only:\n\n" +
                    "Text: " + text);

    Map<String, Object> requestBody = Map.of(
            "model", "gpt-4o-mini",
            "messages", List.of(
                    Map.of("role", systemMessage.getRole(), "content", systemMessage.getContent()),
                    Map.of("role", userMessage.getRole(), "content", userMessage.getContent())
            ),
            "stream", true
    );

    ObjectMapper mapper = new ObjectMapper();

    return webClient.post()
            .uri("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer " + openaiApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                String chunk = new String(bytes);
                return chunk;
            })
            .flatMap(line -> Flux.fromArray(line.split("\n")))
            .filter(line -> line.startsWith("data: "))
            .map(line -> line.replaceFirst("data: ", ""))
            .takeUntil(line -> line.equals("[DONE]"))
            .filter(line -> !line.equals("[DONE]"))
            .flatMap(line -> {
                try {
                    JsonNode node = mapper.readTree(line);
                    JsonNode delta = node.at("/choices/0/delta/content");
                    if (delta.isTextual()) {
                        return Mono.just(delta.asText());
                    }
                    return Mono.empty();
                } catch (Exception e) {
                    return Mono.empty();
                }
            });
}






    @Override
    public Mono<String> getChatbotResponse(JsonNode prompt) {
        return webClient.post()
                .uri("https://helloworld-func-app.azurewebsites.net/api/question")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(prompt)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> System.out.println("AI 서버 응답: " + resp))
                .doOnError(e -> System.err.println("AI 서버 에러: " + e.getMessage()));
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

        // 현재 질문 추가
        ObjectNode currentQuestionNode = mapper.createObjectNode();
        currentQuestionNode.put("speaker", "human");
        currentQuestionNode.put("utterance", koreanQuestion);
        conversationArray.add(currentQuestionNode);

        // 기존 대화 변환
        for (TranslateLog message : recentMessages) {
            ObjectNode messageNode = mapper.createObjectNode();
            String speaker = message.getSender().equalsIgnoreCase("user") ? "human" : "system";
            messageNode.put("speaker", speaker);
            messageNode.put("utterance", message.getContent());
            conversationArray.add(messageNode);
        }


        // 최종 JSON 구조에 추가
        root.set("Conversation", conversationArray);

        System.out.println(root.toString());

        return Mono.just(root);
    }


    @Override
    public Mono<Room> createOrUpdateRoom(String gmail, String roomId, String message) {
        // 만약 roomId가 "new_chat"인 경우 새 방 생성
        if ("new_chat".equals(roomId)) {
            return createTitleFromMessage(message)
                    .flatMap(title -> userRepository.findByEmail(gmail)
                            .flatMap(user -> {
                                Room room = new Room();
                                room.setUserId(user.getId());
                                room.setTitle(title);
                                room.setUpdatedAt(LocalDateTime.now());
                                return roomRepository.save(room);
                            })
                    );
        } else {
            // 기존 방 업데이트
            return userRepository.findByEmail(gmail).flatMap(user -> {
                        return roomRepository.findByUserIdAndId(user.getId(), roomId)
                                .flatMap(existingRoom -> {
                                    existingRoom.setUpdatedAt(LocalDateTime.now());
                                    return roomRepository.save(existingRoom);
                                });
                    }
            );
        }
    }

    @Override
    public Mono<String> createTitleFromMessage(String message) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a helpful assistant who creates short chat titles.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user",
                "Create a concise and meaningful title under 20 characters for the following user message:\n\n" +
                        "\"" + message + "\"\n\n" +
                        "Respond with only the title and nothing else. Make sure it's under 20 characters.");

        GPTRequest request = new GPTRequest("gpt-4o-mini", List.of(systemMessage, userMessage), 100);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> {
                    String title = response.getChoices().get(0).getMessage().getContent().trim();

                    // 양끝 쌍따옴표 제거 (있을 때만)
                    if (title.startsWith("\"") && title.endsWith("\"")) {
                        title = title.substring(1, title.length() - 1).trim();
                    }

                    // 길이 제한 (20자 초과 시 '...' 붙임)
                    return title.length() > 20 ? title.substring(0, 17) + "..." : title;
                });
    }


    @Override
    public Mono<Tuple2<String, List<ChatLogDTO>>> findRecentRoomAndLogs(String gmail) {
        return userRepository.findByEmail(gmail).flatMap(user -> {
            return roomRepository.findFirstByUserIdOrderByUpdatedAtDesc(user.getId())
                    .flatMap(room -> chatMessageRepository.findByRoomIdOrderByTimeAsc(room.getId())
                            .collectList()
                            .map(messages -> Tuples.of(room.getId(), messages.stream()
                                    .map(this::toChatLogDTO)
                                    .collect(Collectors.toList()))
                            ));
        });
    }

    private ChatLogDTO toChatLogDTO(ChatMessage message) {
        return new ChatLogDTO(message.getContent(), message.getSender());
    }
}
