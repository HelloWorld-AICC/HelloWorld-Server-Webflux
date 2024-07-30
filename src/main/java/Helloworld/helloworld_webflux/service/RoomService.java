package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.web.dto.RoomDTO;
import reactor.core.publisher.Flux;

public interface RoomService {
    Flux<RoomDTO> getUserRooms(Long userId);
}