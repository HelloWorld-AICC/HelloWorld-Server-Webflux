package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.domain.Summary;
import Helloworld.helloworld_webflux.domain.TranslateLog;
import Helloworld.helloworld_webflux.repository.SummaryRepository;
import Helloworld.helloworld_webflux.repository.TranslateLogRepository;
import Helloworld.helloworld_webflux.web.dto.GPTRequest;
import Helloworld.helloworld_webflux.web.dto.GPTResponse;
import Helloworld.helloworld_webflux.web.dto.SummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {
    private final TranslateLogRepository translateLogRepository;
    private final SummaryRepository summaryRepository;
    private final WebClient webClient;
    private final UserService userService;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Override
    public Mono<Void> generateSummary(Long userId, String roomId) {
        return translateLogRepository.findByRoomIdOrderByTimeAsc(roomId)
                .collectList()
                .flatMap(messages -> {
                    String concatenatedLogs = messages.stream()
                            .map(TranslateLog::getContent)
                            .reduce((acc, log) -> acc + "\n" + log)
                            .orElse("");

                    return createSummary(concatenatedLogs);
                })
                .flatMap(summaryDTO -> userService.findLanguage(userId)
                        .flatMap(language -> translateToUserLanguage(summaryDTO.getChatSummary(), language)
                                .flatMap(translatedSummary -> {
                                    Summary summary = Summary.builder()
                                            .identificationNum(generateIdentificationNum())
                                            .status("EMPTY")
                                            .title(summaryDTO.getTitle())
                                            .chatSummary(translatedSummary)
                                            .mainPoint(summaryDTO.getMainPoint())
                                            .userId(userId) // userId 대신 user 객체를 사용
                                            .createdAt(LocalDateTime.now())
                                            .updatedAt(LocalDateTime.now())
                                            .build();
                                    return summaryRepository.save(summary);
                                }))
                )
                .then();
    }

    private Mono<SummaryDTO> createSummary(String logs) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a helpful assistant that summarizes chat logs.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user",
                "Summarize the following conversation with a focus on clarity and conciseness. Provide the response in three distinct sections: " +
                        "1. Title (a short title under 30 characters), 2. Chat Summary (a concise summary of the conversation), and 3. Main Points (key takeaways or main points). " +
                        "If any section is not applicable, explicitly state 'None'.\n\n" + logs +
                        "\n\nFormat the response exactly as follows:\nTitle: [Title]\nChat Summary: [Summary]\nMain Points: [Main Points]");

        GPTRequest request = new GPTRequest("gpt-3.5-turbo", List.of(systemMessage, userMessage), 1000);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GPTResponse.class)
                .map(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    String[] parts = content.split("\n");

                    String title = "";
                    String chatSummary = "";
                    String mainPoint = "";

                    for (String part : parts) {
                        if (part.startsWith("Title: ")) {
                            title = part.replace("Title: ", "").trim();
                        } else if (part.startsWith("Chat Summary: ")) {
                            chatSummary = part.replace("Chat Summary: ", "").trim();
                        } else if (part.startsWith("Main Points: ")) {
                            mainPoint = part.replace("Main Points: ", "").trim();
                        }
                    }

                    return new SummaryDTO(title, chatSummary, mainPoint);
                });
    }


    private Mono<String> translateToUserLanguage(String text, String targetLanguage) {
        GPTRequest.Message systemMessage = new GPTRequest.Message("system", "You are a translator.");
        GPTRequest.Message userMessage = new GPTRequest.Message("user", "Translate the following text to " + targetLanguage + ": " + text);
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

    private String generateIdentificationNum() {
        return "ID-" + LocalDateTime.now().toString();
    }
}
