package Helloworld.helloworld_webflux.repository;

import Helloworld.helloworld_webflux.domain.Language;
import Helloworld.helloworld_webflux.domain.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface LanguageRepository extends ReactiveCrudRepository<User,Long>,UserLanguageRepository {
    Mono<String> findLanguageByUserId(Long userId);

}
