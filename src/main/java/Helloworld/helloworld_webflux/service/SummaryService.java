package Helloworld.helloworld_webflux.service;

import reactor.core.publisher.Mono;

public interface SummaryService {
    Mono<Void> generateSummary(Long userId, String roomId);


}
