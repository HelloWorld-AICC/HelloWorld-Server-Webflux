package Helloworld.helloworld_webflux.repository;

import reactor.core.publisher.Mono;

public interface UserCustomRepository {
    Mono<String> findLanguageByUserId(Long UserId);
}
