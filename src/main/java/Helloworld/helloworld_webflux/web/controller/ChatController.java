package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.config.auth.JwtTokenProvider;
import Helloworld.helloworld_webflux.domain.Room;
import Helloworld.helloworld_webflux.service.ChatService;
import Helloworld.helloworld_webflux.service.RoomService;
import Helloworld.helloworld_webflux.service.UserService;
import Helloworld.helloworld_webflux.web.dto.ChatLogDTO;
import Helloworld.helloworld_webflux.web.dto.RecentRoomDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {
    private final ChatService chatService;
    private final UserService userService;
    private final RoomService roomService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/language")
    public Mono<String> getLanguage(@RequestHeader("Authorization") String accessToken) {
        String gmail = jwtTokenProvider.getGoogleEmail(accessToken);
        return userService.findLanguage(gmail);
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestion(@RequestHeader("Authorization") String accessToken,
                                    @RequestParam("roomId") String roomId,
                                    @RequestBody String question) {
        String gmail = jwtTokenProvider.getGoogleEmail(accessToken);
        return chatService.chatAnswer(gmail, roomId, question);
    }


    @GetMapping("/recent-room")
    public Mono<RecentRoomDTO> getRecentRoomAndLogs(@RequestHeader("Authorization") String accessToken) {
        String gmail = jwtTokenProvider.getGoogleEmail(accessToken);
        return chatService.findRecentRoomAndLogs(gmail)
                .map(roomAndLogs -> {
                    String roomId = roomAndLogs.getT1();
                    List<ChatLogDTO> logs = roomAndLogs.getT2();
                    return new RecentRoomDTO(roomId, logs);
                });
    }

    @GetMapping("/room-log")
    public Mono<RecentRoomDTO> getRoomAndLogs(@RequestParam("roomId") String roomId) {
        return roomService.findRoomLogs(roomId);
    }
}
