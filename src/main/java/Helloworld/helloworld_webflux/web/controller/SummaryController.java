package Helloworld.helloworld_webflux.web.controller;

import Helloworld.helloworld_webflux.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/summary")
public class SummaryController {
    private final SummaryService summaryService;
    @PostMapping
    public Mono<Void> makeSummary(@RequestHeader("user_id") Long userId,
                                  @RequestParam("roomId") String roomId) {
        return summaryService.generateSummary(userId, roomId);
    }
}
