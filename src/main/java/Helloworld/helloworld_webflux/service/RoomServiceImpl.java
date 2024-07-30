package Helloworld.helloworld_webflux.service;

import Helloworld.helloworld_webflux.repository.RoomRepository;
import Helloworld.helloworld_webflux.web.dto.RoomDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private final RoomRepository roomRepository;

    @Override
    public Flux<RoomDTO> getUserRooms(Long userId) {
        return roomRepository.findByUserId(userId)
                .map(room -> new RoomDTO(room.getId(), room.getTitle()));
    }
}
