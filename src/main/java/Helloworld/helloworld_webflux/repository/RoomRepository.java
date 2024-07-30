package Helloworld.helloworld_webflux.repository;

import Helloworld.helloworld_webflux.domain.Room;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface RoomRepository extends ReactiveMongoRepository<Room,String> {
    Mono<Room> findByUserIdAndId(Long userId,String roomId);
    Mono<Room> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

}
