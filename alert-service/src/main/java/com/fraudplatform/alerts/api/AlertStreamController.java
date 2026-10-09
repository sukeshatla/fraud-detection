package com.fraudplatform.alerts.api;

import com.fraudplatform.alerts.application.AlertChange;
import com.fraudplatform.alerts.application.AlertChangeBus;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events: a one-way, auto-reconnecting push channel over plain HTTP. It's simpler than
 * WebSockets when the browser only needs to listen.
 *
 * <ul>
 *   <li>One {@link SseEmitter} per connected browser, kept until it completes, times out or errors.
 *   <li>Heartbeat comment every 15 s, so proxies and load balancers don't close an idle connection.
 *   <li>Events carry an id and a name ({@code alert.created} / {@code alert.updated}).
 *   <li>On shutdown every stream is completed first. Otherwise graceful shutdown would wait out
 *       its whole grace period for these never-ending "in-flight requests", and every deploy
 *       would hang. Browsers reconnect automatically, to another instance via the load balancer.
 * </ul>
 */
@RestController
class AlertStreamController {

    static final Duration CLIENT_TIMEOUT = Duration.ofMinutes(30);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicLong eventIds = new AtomicLong();

    AlertStreamController(AlertChangeBus changes) {
        changes.subscribe(this::broadcast);
    }

    @GetMapping(path = "/api/v1/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() throws IOException {
        SseEmitter emitter = new SseEmitter(CLIENT_TIMEOUT.toMillis());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitter.send(SseEmitter.event().comment("connected")); // flush headers so the client sees the stream open
        return emitter;
    }

    void broadcast(AlertChange change) {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(Long.toString(eventIds.incrementAndGet()))
                .name(change.type() == AlertChange.Type.CREATED ? "alert.created" : "alert.updated")
                .data(change.alert(), MediaType.APPLICATION_JSON);
        emitters.forEach(emitter -> send(emitter, event));
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    /** ContextClosedEvent fires before the web server's graceful-shutdown phase starts waiting. */
    @EventListener(ContextClosedEvent.class)
    void completeAllStreams() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    int connectedClients() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter); // client went away
            emitter.completeWithError(e);
        }
    }
}
