package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.service.RoomService;
import Helloworld.helloworld_webflux.service.UserService;
import Helloworld.helloworld_webflux.web.dto.RoomDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final RoomService roomService;


    @GetMapping("/")
    @Operation(summary = "언어 조회 API", description = "언어 조회 화면 test API입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "COMMON200", description = "OK, 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "USER4001", description = "사용자를 찾을수 없습니다.")
    })
    @Parameters({
            @Parameter(name = "user_id", description = "RequestHeader - 로그인한 사용자 아이디(accessToken으로 변경 예정)"),
    })
    public Mono<String> getLanguage(@RequestHeader("user_id") Long userId){
        return userService.findLanguage(userId);
    }

    @GetMapping("/room-list")
    public Flux<RoomDTO> getUserRooms(@RequestHeader("user_id") Long userId) {
        return roomService.getUserRooms(userId);
    }
}
