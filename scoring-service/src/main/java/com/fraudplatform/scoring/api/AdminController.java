package com.fraudplatform.scoring.api;

import com.fraudplatform.scoring.application.DeadLetterReplay;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints. Restricted to an admin role in Feature 015. */
@RestController
@RequestMapping("/admin")
class AdminController {

    record ReplayResult(int replayed) {}

    private final DeadLetterReplay replay;

    AdminController(DeadLetterReplay replay) {
        this.replay = replay;
    }

    @PostMapping("/dlt/replay")
    ReplayResult replayDeadLetters(@RequestParam(defaultValue = "100") int max) {
        return new ReplayResult(replay.replay(Math.clamp(max, 1, 1000)));
    }
}
