package Helloworld.helloworld_webflux.repository;

import Helloworld.helloworld_webflux.domain.Language;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserLanguageRepository {
    Mono<String> findLanguageByUserId(Long userId);
}
